package com.app.n8n.process

import android.content.Context
import android.util.Log
import com.app.n8n.extractor.FileExtractor
import com.app.n8n.model.LogEntry
import com.app.n8n.model.LogLevel
import com.app.n8n.model.ServerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

class N8nProcessSupervisor(private val context: Context) {

    companion object {
        private const val TAG = "N8nSupervisor"
        private const val DEFAULT_PORT = 5678
        private const val SHUTDOWN_TIMEOUT_SECONDS = 10L
    }

    private val extractor = FileExtractor(context)
    private var process: Process? = null
    private var stdoutJob: Job? = null
    private var stderrJob: Job? = null
    private val supervisorScope = CoroutineScope(Dispatchers.IO + Job())

    private val _serverState = MutableStateFlow(ServerState.STOPPED)
    val serverState: StateFlow<ServerState> = _serverState.asStateFlow()

    private val _logFlow = MutableSharedFlow<LogEntry>(replay = 200, extraBufferCapacity = 500)
    val logFlow: SharedFlow<LogEntry> = _logFlow.asSharedFlow()

    fun isRunning(): Boolean = _serverState.value == ServerState.RUNNING || _serverState.value == ServerState.STARTING

    suspend fun startServer(localIp: String, port: Int = DEFAULT_PORT) = withContext(Dispatchers.IO) {
        if (isRunning()) {
            Log.w(TAG, "Server already running or starting")
            return@withContext
        }

        _serverState.value = ServerState.STARTING
        emitLog("Initializing n8n server on $localIp:$port...", LogLevel.INFO)

        val nativeLibDir = context.applicationInfo.nativeLibraryDir
        val nativeProot = File(nativeLibDir, "libproot.so")
        val prootBin = if (nativeProot.exists()) nativeProot else extractor.prootBinary
        val rootfsDir = extractor.rootfsDir
        val dataDir = extractor.dataDir
        val tmpDir = File(context.cacheDir, "proot_tmp").apply { mkdirs() }

        // Validate PRoot binary
        if (!prootBin.exists()) {
            emitLog("Error: PRoot binary not found at ${prootBin.absolutePath}", LogLevel.ERROR)
            _serverState.value = ServerState.ERROR
            return@withContext
        }
        prootBin.setExecutable(true, false)

        val webhookUrl = "http://$localIp:$port/"

        val commandList = listOf(
            prootBin.absolutePath,
            "-0",
            "-r", rootfsDir.absolutePath,
            "-b", "/dev",
            "-b", "/proc",
            "-b", "/sys",
            "-b", "${dataDir.absolutePath}:/root/.n8n",
            "-b", "${tmpDir.absolutePath}:/tmp",
            "-w", "/root",
            "/usr/bin/env", "-i",
            "HOME=/root",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "NODE_OPTIONS=--max-old-space-size=512",
            "N8N_HOST=0.0.0.0",
            "N8N_PORT=$port",
            "N8N_SECURE_COOKIE=false",
            "N8N_DIAGNOSTICS_ENABLED=false",
            "WEBHOOK_URL=$webhookUrl",
            "/usr/local/bin/n8n", "start"
        )

        try {
            emitLog("Executing supervisor via ${prootBin.name} (Native lib dir: $nativeLibDir)", LogLevel.DEBUG)

            val processBuilder = ProcessBuilder(commandList)
                .directory(context.filesDir)
                .redirectErrorStream(false)

            val env = processBuilder.environment()
            env["LD_LIBRARY_PATH"] = nativeLibDir
            val loader = File(nativeLibDir, "libproot-loader.so")
            if (loader.exists()) {
                env["PROOT_LOADER"] = loader.absolutePath
            }
            val loader32 = File(nativeLibDir, "libproot-loader32.so")
            if (loader32.exists()) {
                env["PROOT_LOADER_32"] = loader32.absolutePath
            }
            env["PROOT_TMP_DIR"] = tmpDir.absolutePath

            val p = processBuilder.start()
            process = p

            // Monitor stdout
            stdoutJob = supervisorScope.launch {
                val reader = BufferedReader(InputStreamReader(p.inputStream))
                try {
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        parseAndEmitLog(line, isStderr = false)
                    }
                } catch (e: Exception) {
                    if (isActive) Log.e(TAG, "Stdout reader closed", e)
                } finally {
                    reader.close()
                }
            }

            // Monitor stderr
            stderrJob = supervisorScope.launch {
                val reader = BufferedReader(InputStreamReader(p.errorStream))
                try {
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        parseAndEmitLog(line, isStderr = true)
                    }
                } catch (e: Exception) {
                    if (isActive) Log.e(TAG, "Stderr reader closed", e)
                } finally {
                    reader.close()
                }
            }

            // Monitor process exit
            supervisorScope.launch {
                try {
                    val exitCode = p.waitFor()
                    emitLog("n8n process exited with code $exitCode", if (exitCode == 0) LogLevel.INFO else LogLevel.WARN)
                } catch (e: InterruptedException) {
                    emitLog("Process wait interrupted", LogLevel.WARN)
                } finally {
                    _serverState.value = ServerState.STOPPED
                    cleanupHandles()
                }
            }

            _serverState.value = ServerState.RUNNING
            emitLog("n8n server engine started successfully.", LogLevel.INFO)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start n8n process", e)
            emitLog("Failed to start process: ${e.localizedMessage}", LogLevel.ERROR)
            _serverState.value = ServerState.ERROR
            cleanupHandles()
        }
    }

    suspend fun stopServer() = withContext(Dispatchers.IO) {
        if (_serverState.value == ServerState.STOPPED) return@withContext

        _serverState.value = ServerState.STOPPING
        emitLog("Stopping n8n server gracefully (allowing WAL flush)...", LogLevel.INFO)

        val p = process
        if (p != null) {
            try {
                p.destroy() // Sends SIGTERM

                var exited = false
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    exited = p.waitFor(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                } else {
                    val startTime = System.currentTimeMillis()
                    while (System.currentTimeMillis() - startTime < SHUTDOWN_TIMEOUT_SECONDS * 1000) {
                        try {
                            p.exitValue()
                            exited = true
                            break
                        } catch (e: IllegalThreadStateException) {
                            Thread.sleep(200)
                        }
                    }
                }

                if (!exited) {
                    emitLog("Process did not stop within ${SHUTDOWN_TIMEOUT_SECONDS}s. Force killing...", LogLevel.WARN)
                    p.destroyForcibly()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping process", e)
                p.destroyForcibly()
            }
        }

        cleanupHandles()
        _serverState.value = ServerState.STOPPED
        emitLog("n8n server is stopped.", LogLevel.INFO)
    }

    private fun parseAndEmitLog(rawLine: String, isStderr: Boolean) {
        val level = when {
            rawLine.contains("ERROR", ignoreCase = true) || isStderr -> LogLevel.ERROR
            rawLine.contains("WARN", ignoreCase = true) -> LogLevel.WARN
            rawLine.contains("DEBUG", ignoreCase = true) -> LogLevel.DEBUG
            else -> LogLevel.INFO
        }
        supervisorScope.launch {
            _logFlow.emit(LogEntry(message = rawLine, level = level))
        }
    }

    private suspend fun emitLog(message: String, level: LogLevel) {
        _logFlow.emit(LogEntry(message = message, level = level))
    }

    private fun cleanupHandles() {
        stdoutJob?.cancel()
        stderrJob?.cancel()
        stdoutJob = null
        stderrJob = null
        process = null
    }
}
