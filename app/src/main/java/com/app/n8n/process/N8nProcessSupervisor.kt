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
    private var logMonitorJob: Job? = null
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

        // Pre-flight check 1: PRoot binary
        if (!prootBin.exists()) {
            emitLog("Error: PRoot binary not found at ${prootBin.absolutePath}", LogLevel.ERROR)
            _serverState.value = ServerState.ERROR
            return@withContext
        }

        // Pre-flight check 2: Rootfs /bin/sh or /bin/busybox
        val shFile = File(rootfsDir, "bin/sh")
        val busyboxFile = File(rootfsDir, "bin/busybox")

        if (!shFile.exists() && busyboxFile.exists()) {
            try {
                busyboxFile.copyTo(shFile, overwrite = true)
                shFile.setExecutable(true, false)
            } catch (e: Exception) {
                Log.w(TAG, "Error copying busybox to sh", e)
            }
        }

        if (!shFile.exists() && !busyboxFile.exists()) {
            emitLog("Error: Rootfs shell not found at ${shFile.absolutePath}. Please re-run setup.", LogLevel.ERROR)
            _serverState.value = ServerState.ERROR
            return@withContext
        }
        shFile.setExecutable(true, false)
        busyboxFile.setExecutable(true, false)

        // Ensure DNS & networking config in rootfs
        val etcDir = File(rootfsDir, "etc").apply { mkdirs() }
        File(etcDir, "resolv.conf").writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
        File(etcDir, "hosts").writeText("127.0.0.1 localhost\n::1 localhost\n")

        val webhookUrl = "http://$localIp:$port/"
        val shellPrefix = getShellPrefix(rootfsDir)

        // Check if n8n and node are already installed and verified
        var isInstalledAndVerified = false
        val hasN8n = File(rootfsDir, "usr/local/bin/n8n").exists() || File(rootfsDir, "usr/bin/n8n").exists()
        val hasNode = File(rootfsDir, "usr/bin/node").exists() || File(rootfsDir, "usr/local/bin/node").exists()

        if (hasN8n && hasNode) {
            val quickNode = runProotCommand(
                prootBin, rootfsDir, dataDir, tmpDir, nativeLibDir,
                shellPrefix + listOf("node --version"),
                "Verifying existing Node.js"
            )
            val quickNpm = runProotCommand(
                prootBin, rootfsDir, dataDir, tmpDir, nativeLibDir,
                shellPrefix + listOf("npm --version"),
                "Verifying existing npm"
            )
            val quickN8n = runProotCommand(
                prootBin, rootfsDir, dataDir, tmpDir, nativeLibDir,
                shellPrefix + listOf("n8n --version"),
                "Verifying existing n8n"
            )
            if (quickNode && quickNpm && quickN8n) {
                isInstalledAndVerified = true
            }
        }

        if (!isInstalledAndVerified) {
            emitLog("Starting step-by-step automated package bootstrap...", LogLevel.INFO)

            // Step 1: Harmless shell test
            val step1 = runProotCommand(
                prootBin, rootfsDir, dataDir, tmpDir, nativeLibDir,
                shellPrefix + listOf("echo SHELL_OK"),
                "Testing rootfs shell execution (echo SHELL_OK)"
            )
            if (!step1) {
                emitLog("Rootfs shell execution failed. Cannot proceed with package setup.", LogLevel.ERROR)
                _serverState.value = ServerState.ERROR
                return@withContext
            }

            // Step 2: Test apk update separately
            val step2 = runProotCommand(
                prootBin, rootfsDir, dataDir, tmpDir, nativeLibDir,
                shellPrefix + listOf("apk update"),
                "Updating Alpine package repositories (apk update)"
            )
            if (!step2) {
                emitLog("apk update failed. Check network or repository configuration.", LogLevel.ERROR)
                _serverState.value = ServerState.ERROR
                return@withContext
            }

            // Step 3: Install Node.js, npm, and prerequisites via apk
            val step3 = runProotCommand(
                prootBin, rootfsDir, dataDir, tmpDir, nativeLibDir,
                shellPrefix + listOf("apk add --no-cache nodejs npm sqlite ca-certificates bash python3 make g++"),
                "Installing Node.js, npm, and build dependencies via apk"
            )
            if (!step3) {
                emitLog("Failed to install Node.js/npm dependencies via apk.", LogLevel.ERROR)
                _serverState.value = ServerState.ERROR
                return@withContext
            }

            // Step 4: Install n8n globally via npm
            val step4 = runProotCommand(
                prootBin, rootfsDir, dataDir, tmpDir, nativeLibDir,
                shellPrefix + listOf("npm install -g n8n --omit=dev --foreground-scripts"),
                "Installing n8n globally via npm (this may take 2-4 minutes)"
            )
            if (!step4) {
                emitLog("Failed to install n8n globally via npm.", LogLevel.ERROR)
                _serverState.value = ServerState.ERROR
                return@withContext
            }

            // Step 5: Verification of node, npm, and n8n
            val verifyNode = runProotCommand(
                prootBin, rootfsDir, dataDir, tmpDir, nativeLibDir,
                shellPrefix + listOf("node --version"),
                "Verifying Node.js version"
            )
            val verifyNpm = runProotCommand(
                prootBin, rootfsDir, dataDir, tmpDir, nativeLibDir,
                shellPrefix + listOf("npm --version"),
                "Verifying npm version"
            )
            val verifyN8n = runProotCommand(
                prootBin, rootfsDir, dataDir, tmpDir, nativeLibDir,
                shellPrefix + listOf("n8n --version"),
                "Verifying n8n version"
            )

            if (!verifyNode || !verifyNpm || !verifyN8n) {
                emitLog("Verification failed: node, npm, or n8n did not execute properly inside rootfs.", LogLevel.ERROR)
                _serverState.value = ServerState.ERROR
                return@withContext
            }

            emitLog("n8n package setup verified and completed successfully!", LogLevel.INFO)
        }

        // Launch n8n start via shell prefix to auto-resolve PATH
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
            "WEBHOOK_URL=$webhookUrl"
        ) + shellPrefix + listOf("exec n8n start")

        try {
            emitLog("Executing supervisor command:\n${commandList.joinToString(" ")}", LogLevel.DEBUG)

            val processBuilder = ProcessBuilder(commandList)
                .directory(context.filesDir)
                .redirectErrorStream(true) // Merge stdout & stderr for complete chronological logs

            setupEnvironment(processBuilder, nativeLibDir, tmpDir)

            val p = processBuilder.start()
            process = p

            // Monitor combined output stream
            logMonitorJob = supervisorScope.launch {
                val reader = BufferedReader(InputStreamReader(p.inputStream))
                try {
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        parseAndEmitLog(line)
                    }
                } catch (e: Exception) {
                    if (isActive) Log.e(TAG, "Log stream closed", e)
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

    private fun getShellPrefix(rootfsDir: File): List<String> {
        val shFile = File(rootfsDir, "bin/sh")
        val busyboxFile = File(rootfsDir, "bin/busybox")
        return when {
            shFile.exists() -> listOf("/bin/sh", "-c")
            busyboxFile.exists() -> listOf("/bin/busybox", "sh", "-c")
            else -> listOf("/bin/sh", "-c")
        }
    }

    private suspend fun runProotCommand(
        prootBin: File,
        rootfsDir: File,
        dataDir: File,
        tmpDir: File,
        nativeLibDir: String,
        innerCommand: List<String>,
        stepDescription: String = ""
    ): Boolean = withContext(Dispatchers.IO) {
        val fullCommand = mutableListOf(
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
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
        )
        fullCommand.addAll(innerCommand)

        val desc = if (stepDescription.isNotEmpty()) stepDescription else innerCommand.joinToString(" ")
        emitLog("Executing step: $desc", LogLevel.INFO)

        try {
            val pb = ProcessBuilder(fullCommand)
                .directory(context.filesDir)
                .redirectErrorStream(true)

            setupEnvironment(pb, nativeLibDir, tmpDir)
            val p = pb.start()

            val reader = BufferedReader(InputStreamReader(p.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { parseAndEmitLog(it) }
            }
            reader.close()

            val exitCode = p.waitFor()
            if (exitCode == 0) {
                emitLog("Step completed successfully (exit code $exitCode)", LogLevel.INFO)
                return@withContext true
            } else {
                emitLog("Step failed with exit code $exitCode", LogLevel.ERROR)
                return@withContext false
            }
        } catch (e: Exception) {
            emitLog("Step threw exception: ${e.localizedMessage}", LogLevel.ERROR)
            return@withContext false
        }
    }

    private fun setupEnvironment(processBuilder: ProcessBuilder, nativeLibDir: String, tmpDir: File) {
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

    private fun parseAndEmitLog(rawLine: String) {
        // Filter or tag linker notices as DEBUG so they don't look like app crashes
        val level = when {
            rawLine.startsWith("WARNING: linker:") -> LogLevel.DEBUG
            rawLine.contains("ERROR", ignoreCase = true) -> LogLevel.ERROR
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
        logMonitorJob?.cancel()
        logMonitorJob = null
        process = null
    }
}
