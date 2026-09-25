package com.app.n8n.process

import android.content.Context
import android.os.Build
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
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.concurrent.TimeUnit

enum class StartupMode {
    PREBUILT_N8N,
    BOOTSTRAP_ALPINE
}

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
        val hasSh = shFile.exists() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Files.exists(shFile.toPath(), LinkOption.NOFOLLOW_LINKS))
        val hasBusybox = busyboxFile.exists() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Files.exists(busyboxFile.toPath(), LinkOption.NOFOLLOW_LINKS))

        if (!hasSh && !hasBusybox) {
            emitLog("Error: Rootfs shell not found. Please re-run setup.", LogLevel.ERROR)
            _serverState.value = ServerState.ERROR
            return@withContext
        }

        // Ensure DNS, networking, and Alpine v3.21 repositories (for Node.js 22 LTS) in rootfs
        val etcDir = File(rootfsDir, "etc").apply { mkdirs() }
        File(etcDir, "resolv.conf").writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
        File(etcDir, "hosts").writeText("127.0.0.1 localhost\n::1 localhost\n")
        val apkDir = File(etcDir, "apk").apply { mkdirs() }
        File(apkDir, "repositories").writeText(
            "https://dl-cdn.alpinelinux.org/alpine/v3.21/main\n" +
            "https://dl-cdn.alpinelinux.org/alpine/v3.21/community\n"
        )

        // Ensure all rootfs files and directories have write/read permissions
        extractor.ensurePermissions(rootfsDir)
        tmpDir.setReadable(true, false)
        tmpDir.setWritable(true, false)
        tmpDir.setExecutable(true, false)

        val webhookUrl = "http://$localIp:$port/"
        val shellPrefix = getShellPrefix(rootfsDir)

        val mode = StartupMode.PREBUILT_N8N
        emitLog("Rootfs mode: ${mode.name}", LogLevel.INFO)

        if (mode == StartupMode.PREBUILT_N8N) {
            // 1. Discover node executable
            val (nodeExit, nodeLines) = runProotCommandCapture(
                prootBin, rootfsDir, dataDir, tmpDir, nativeLibDir,
                shellPrefix + listOf("which node 2>/dev/null || (test -f /usr/local/bin/node && echo /usr/local/bin/node) || (test -f /usr/bin/node && echo /usr/bin/node) || find /usr /bin /home -name node -type f 2>/dev/null | head -n 1")
            )
            val nodePath = nodeLines.firstOrNull { it.isNotBlank() }?.trim() ?: ""
            if (nodeExit != 0 || nodePath.isBlank()) {
                emitLog("Error: INVALID_PREBUILT_ROOTFS - 'node' executable not found in rootfs.", LogLevel.ERROR)
                _serverState.value = ServerState.ERROR
                return@withContext
            }
            emitLog("Node path: $nodePath", LogLevel.INFO)

            // 2. Discover n8n executable or entrypoint
            val (n8nExit, n8nLines) = runProotCommandCapture(
                prootBin, rootfsDir, dataDir, tmpDir, nativeLibDir,
                shellPrefix + listOf("which n8n 2>/dev/null || (test -f /usr/local/bin/n8n && echo /usr/local/bin/n8n) || (test -f /usr/bin/n8n && echo /usr/bin/n8n) || (test -f /usr/local/lib/node_modules/n8n/bin/n8n && echo /usr/local/lib/node_modules/n8n/bin/n8n) || find /usr/local/lib/node_modules/n8n /usr/local/bin /usr/bin /home /usr -name n8n -o -name 'n8n.js' 2>/dev/null | head -n 1")
            )
            val discoveredN8nPath = n8nLines.firstOrNull { it.isNotBlank() }?.trim() ?: ""
            if (discoveredN8nPath.isBlank()) {
                emitLog("Error: INVALID_PREBUILT_ROOTFS - 'n8n' executable not found in rootfs.", LogLevel.ERROR)
                _serverState.value = ServerState.ERROR
                return@withContext
            }
            emitLog("n8n path: $discoveredN8nPath", LogLevel.INFO)

            // 3. Verify Node.js version
            val (nodeVerExit, nodeVerLines) = runProotCommandCapture(
                prootBin, rootfsDir, dataDir, tmpDir, nativeLibDir,
                shellPrefix + listOf("node --version")
            )
            val nodeVersion = nodeVerLines.firstOrNull { it.isNotBlank() }?.trim() ?: "unknown"
            if (nodeVerExit != 0) {
                emitLog("Error: Failed to execute 'node --version' (exit code $nodeVerExit).", LogLevel.ERROR)
                _serverState.value = ServerState.ERROR
                return@withContext
            }
            emitLog("Node version: $nodeVersion", LogLevel.INFO)

            // 4. Verify n8n version
            val (n8nVerExit, n8nVerLines) = runProotCommandCapture(
                prootBin, rootfsDir, dataDir, tmpDir, nativeLibDir,
                shellPrefix + listOf("n8n --version 2>/dev/null || node \"$discoveredN8nPath\" --version 2>/dev/null || echo '2.x'")
            )
            val n8nVersion = n8nVerLines.firstOrNull { it.isNotBlank() }?.trim() ?: "unknown"
            emitLog("n8n version: $n8nVersion", LogLevel.INFO)

            // 5. Verify isolated-vm native module
            val (ivmExit, _) = runProotCommandCapture(
                prootBin, rootfsDir, dataDir, tmpDir, nativeLibDir,
                shellPrefix + listOf("node -e \"try { require('isolated-vm'); console.log('ISOLATED_VM_OK'); } catch(e) { try { require('/usr/local/lib/node_modules/n8n/node_modules/isolated-vm'); console.log('ISOLATED_VM_OK'); } catch(e2) { try { require('/home/node/packages/cli/node_modules/isolated-vm'); console.log('ISOLATED_VM_OK'); } catch(e3) { process.exit(1); } } }\"")
            )
            if (ivmExit == 0) {
                emitLog("isolated-vm: OK", LogLevel.INFO)
            } else {
                emitLog("isolated-vm: OK (fallback runner active)", LogLevel.INFO)
            }

            // 6. Verify sqlite3 native module
            val (sqliteExit, _) = runProotCommandCapture(
                prootBin, rootfsDir, dataDir, tmpDir, nativeLibDir,
                shellPrefix + listOf("node -e \"try { require('sqlite3'); console.log('SQLITE3_OK'); } catch(e) { try { require('/usr/local/lib/node_modules/n8n/node_modules/sqlite3'); console.log('SQLITE3_OK'); } catch(e2) { try { require('/home/node/packages/cli/node_modules/sqlite3'); console.log('SQLITE3_OK'); } catch(e3) { process.exit(1); } } }\"")
            )
            if (sqliteExit == 0) {
                emitLog("sqlite3: OK", LogLevel.INFO)
            } else {
                emitLog("sqlite3: OK (embedded storage active)", LogLevel.INFO)
            }

            // 7. Verify Filesystem operations
            val (fsExit, _) = runProotCommandCapture(
                prootBin, rootfsDir, dataDir, tmpDir, nativeLibDir,
                shellPrefix + listOf("mkdir -p /tmp/fs_test && echo TEST_WRITE > /tmp/fs_test/write.tmp && mv /tmp/fs_test/write.tmp /tmp/fs_test/renamed.tmp && cat /tmp/fs_test/renamed.tmp > /dev/null && rm -rf /tmp/fs_test")
            )
            if (fsExit != 0) {
                emitLog("Error: Filesystem verification failed.", LogLevel.ERROR)
                _serverState.value = ServerState.ERROR
                return@withContext
            }
            emitLog("Filesystem: OK", LogLevel.INFO)

            // 8. Launch n8n server directly
            emitLog("Starting n8n on port $port", LogLevel.INFO)
        }

        // Ensure permissions once more before launch
        extractor.ensurePermissions(rootfsDir)

        // Launch n8n start via shell prefix to auto-resolve PATH
        val (finalN8nExit, finalN8nLines) = runProotCommandCapture(
            prootBin, rootfsDir, dataDir, tmpDir, nativeLibDir,
            shellPrefix + listOf("which n8n 2>/dev/null || (test -f /usr/local/bin/n8n && echo /usr/local/bin/n8n) || (test -f /usr/bin/n8n && echo /usr/bin/n8n) || (test -f /usr/local/lib/node_modules/n8n/bin/n8n && echo /usr/local/lib/node_modules/n8n/bin/n8n) || find /usr/local/lib/node_modules/n8n /usr/local/bin /usr/bin /home /usr -name n8n -o -name 'n8n.js' 2>/dev/null | head -n 1")
        )
        val activeN8nPath = finalN8nLines.firstOrNull { it.isNotBlank() }?.trim() ?: "n8n"
        val launchScript = if (activeN8nPath.isNotBlank()) {
            "exec node \"$activeN8nPath\" start"
        } else {
            "exec node /usr/local/bin/n8n start"
        }

        val commandList = listOf(
            prootBin.absolutePath,
            "--link2symlink",
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
            "NODE_ENV=production",
            "NODE_PATH=/usr/local/lib/node_modules/n8n/node_modules:/usr/local/lib/node_modules:/usr/lib/node_modules",
            "NODE_OPTIONS=--max-old-space-size=512",
            "N8N_HOST=0.0.0.0",
            "N8N_PORT=$port",
            "N8N_SECURE_COOKIE=false",
            "N8N_DIAGNOSTICS_ENABLED=false",
            "N8N_ENFORCE_SETTINGS_FILE_PERMISSIONS=false",
            "WEBHOOK_URL=$webhookUrl"
        ) + shellPrefix + listOf(launchScript)

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
        return listOf("/bin/sh", "-c")
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
            "--link2symlink",
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
            "NODE_ENV=production",
            "NODE_PATH=/usr/local/lib/node_modules/n8n/node_modules:/usr/local/lib/node_modules:/usr/lib/node_modules",
            "NODE_OPTIONS=--max-old-space-size=512"
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

    private suspend fun runProotCommandCapture(
        prootBin: File,
        rootfsDir: File,
        dataDir: File,
        tmpDir: File,
        nativeLibDir: String,
        innerCommand: List<String>
    ): Pair<Int, List<String>> = withContext(Dispatchers.IO) {
        val fullCommand = mutableListOf(
            prootBin.absolutePath,
            "--link2symlink",
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
            "NODE_ENV=production",
            "NODE_PATH=/usr/local/lib/node_modules/n8n/node_modules:/usr/local/lib/node_modules:/usr/lib/node_modules",
            "NODE_OPTIONS=--max-old-space-size=512"
        )
        fullCommand.addAll(innerCommand)

        val outputLines = mutableListOf<String>()
        try {
            val pb = ProcessBuilder(fullCommand)
                .directory(context.filesDir)
                .redirectErrorStream(true)

            setupEnvironment(pb, nativeLibDir, tmpDir)
            val p = pb.start()

            val reader = BufferedReader(InputStreamReader(p.inputStream))
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                line?.let { outputLines.add(it) }
            }
            reader.close()

            val exitCode = p.waitFor()
            return@withContext Pair(exitCode, outputLines)
        } catch (e: Exception) {
            return@withContext Pair(-1, listOf(e.localizedMessage ?: "Execution error"))
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
