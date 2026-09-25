package com.app.n8n.extractor

import android.content.Context
import android.os.Build
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Paths

sealed class ExtractionProgress {
    data class Progress(val percentage: Float, val currentTask: String) : ExtractionProgress()
    data class Completed(val rootfsDir: File, val binDir: File, val dataDir: File) : ExtractionProgress()
    data class Failed(val error: Throwable) : ExtractionProgress()
}

class FileExtractor(private val context: Context) {

    companion object {
        private const val TAG = "FileExtractor"
        const val ROOTFS_XZ_ASSET = "n8n-rootfs-arm64.tar.xz"
        const val ROOTFS_GZ_ASSET = "n8n-rootfs-arm64.tar.gz"
        const val ALPINE_MINI_URL = "https://dl-cdn.alpinelinux.org/alpine/v3.20/releases/aarch64/alpine-minirootfs-3.20.0-aarch64.tar.gz"
        private const val SENTINEL_FILE = ".n8n_installed"
    }

    val binDir: File get() = File(context.filesDir, "bin")
    val rootfsDir: File get() = File(context.filesDir, "rootfs")
    val dataDir: File get() = File(context.filesDir, "data/.n8n")

    val prootBinary: File
        get() {
            val nativeBin = File(context.applicationInfo.nativeLibraryDir, "libproot.so")
            return if (nativeBin.exists()) nativeBin else File(binDir, "proot")
        }

    fun isInstalled(): Boolean {
        val sentinel = File(context.filesDir, SENTINEL_FILE)
        val busybox = File(rootfsDir, "bin/busybox")
        val shFile = File(rootfsDir, "bin/sh")
        val hasShell = busybox.exists() || shFile.exists() || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Files.exists(shFile.toPath(), LinkOption.NOFOLLOW_LINKS))
        return sentinel.exists() && hasShell && prootBinary.exists()
    }

    fun extractPayload(): Flow<ExtractionProgress> = flow {
        try {
            emit(ExtractionProgress.Progress(0.02f, "Preparing file system sandbox..."))

            binDir.mkdirs()
            rootfsDir.mkdirs()
            dataDir.mkdirs()

            // Step 1: Detect bundled rootfs asset
            var assetName: String? = null
            try {
                context.assets.open(ROOTFS_GZ_ASSET).close()
                assetName = ROOTFS_GZ_ASSET
            } catch (e1: Exception) {
                try {
                    context.assets.open(ROOTFS_XZ_ASSET).close()
                    assetName = ROOTFS_XZ_ASSET
                } catch (e2: Exception) {
                    assetName = null
                }
            }

            if (assetName != null) {
                emit(ExtractionProgress.Progress(0.10f, "Extracting bundled rootfs payload ($assetName)..."))
                context.assets.open(assetName).use { assetStream ->
                    if (assetName.endsWith(".xz")) {
                        extractTarXzStream(assetStream) { p, msg ->
                            emit(ExtractionProgress.Progress(0.10f + (p * 0.85f), msg))
                        }
                    } else {
                        extractTarGzStream(assetStream) { p, msg ->
                            emit(ExtractionProgress.Progress(0.10f + (p * 0.85f), msg))
                        }
                    }
                }
            } else {
                // Download Alpine Linux base rootfs from CDN
                emit(ExtractionProgress.Progress(0.10f, "Downloading Alpine Linux ARM64 rootfs..."))
                val tempArchive = File(context.cacheDir, "alpine-rootfs.tar.gz")
                downloadFile(ALPINE_MINI_URL, tempArchive) { downloadProgress ->
                    emit(ExtractionProgress.Progress(0.10f + (downloadProgress * 0.40f), "Downloading rootfs: ${(downloadProgress * 100).toInt()}%"))
                }

                emit(ExtractionProgress.Progress(0.55f, "Extracting Alpine Linux rootfs..."))
                FileInputStream(tempArchive).use { fileIn ->
                    extractTarGzStream(fileIn) { p, msg ->
                        emit(ExtractionProgress.Progress(0.55f + (p * 0.40f), msg))
                    }
                }
                tempArchive.delete()
            }

            // Configure DNS resolv.conf and Alpine v3.21 repositories (for Node.js 22 LTS)
            val etcDir = File(rootfsDir, "etc").apply { mkdirs() }
            File(etcDir, "resolv.conf").writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")
            File(etcDir, "hosts").writeText("127.0.0.1 localhost\n::1 localhost\n")
            val apkDir = File(etcDir, "apk").apply { mkdirs() }
            File(apkDir, "repositories").writeText(
                "https://dl-cdn.alpinelinux.org/alpine/v3.21/main\n" +
                "https://dl-cdn.alpinelinux.org/alpine/v3.21/community\n"
            )

            // Ensure busybox and sh are executable and valid
            val busybox = File(rootfsDir, "bin/busybox")
            val shFile = File(rootfsDir, "bin/sh")

            if (busybox.exists()) {
                busybox.setReadable(true, false)
                busybox.setWritable(true, false)
                busybox.setExecutable(true, false)
                // If sh does not exist as a direct file or broken symlink, duplicate busybox as sh
                if (!shFile.exists()) {
                    try {
                        busybox.copyTo(shFile, overwrite = true)
                        shFile.setReadable(true, false)
                        shFile.setWritable(true, false)
                        shFile.setExecutable(true, false)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not copy busybox to sh", e)
                    }
                }
            }

            // Step 2: Finalize permissions
            emit(ExtractionProgress.Progress(0.96f, "Configuring POSIX permissions..."))
            ensurePermissions(rootfsDir)

            File(context.filesDir, SENTINEL_FILE).writeText(System.currentTimeMillis().toString())

            emit(ExtractionProgress.Progress(1.0f, "Environment ready!"))
            emit(ExtractionProgress.Completed(rootfsDir, binDir, dataDir))

        } catch (t: Throwable) {
            Log.e(TAG, "Rootfs extraction failed", t)
            emit(ExtractionProgress.Failed(t))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun downloadFile(urlStr: String, destination: File, onProgress: suspend (Float) -> Unit) {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.connectTimeout = 15000
        connection.readTimeout = 30000
        connection.instanceFollowRedirects = true
        connection.connect()

        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("HTTP ${connection.responseCode}: ${connection.responseMessage}")
        }

        val totalLength = connection.contentLength
        var downloaded = 0L

        destination.parentFile?.mkdirs()
        connection.inputStream.use { input ->
            FileOutputStream(destination).use { output ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    if (totalLength > 0) {
                        val progress = downloaded.toFloat() / totalLength
                        onProgress(progress)
                    }
                }
            }
        }
    }

    private suspend fun extractTarGzStream(
        rawInput: InputStream,
        onProgress: suspend (Float, String) -> Unit
    ) {
        val bufferedInput = BufferedInputStream(rawInput)
        val gzIn = GzipCompressorInputStream(bufferedInput)
        val tarIn = TarArchiveInputStream(gzIn)
        extractTarEntries(tarIn, onProgress)
    }

    private suspend fun extractTarXzStream(
        rawInput: InputStream,
        onProgress: suspend (Float, String) -> Unit
    ) {
        val bufferedInput = BufferedInputStream(rawInput)
        val xzIn = XZInputStream(bufferedInput)
        val tarIn = TarArchiveInputStream(xzIn)
        extractTarEntries(tarIn, onProgress)
    }

    private suspend fun extractTarEntries(
        tarIn: TarArchiveInputStream,
        onProgress: suspend (Float, String) -> Unit
    ) {
        var entry: TarArchiveEntry? = tarIn.nextTarEntry
        var count = 0

        while (entry != null) {
            count++
            val cleanName = entry.name.removePrefix("./").removePrefix("/")
            if (cleanName.isNotEmpty()) {
                val destFile = File(rootfsDir, cleanName)

                if (entry.isDirectory) {
                    destFile.mkdirs()
                    destFile.setReadable(true, false)
                    destFile.setWritable(true, false)
                    destFile.setExecutable(true, false)
                } else if (entry.isSymbolicLink) {
                    createSymlink(destFile, entry.linkName)
                } else {
                    destFile.parentFile?.mkdirs()
                    destFile.parentFile?.setWritable(true, false)
                    FileOutputStream(destFile).use { output ->
                        tarIn.copyTo(output)
                    }
                    destFile.setReadable(true, false)
                    destFile.setWritable(true, false)
                    if (entry.mode and 0b001001001 != 0) {
                        destFile.setExecutable(true, false)
                    }
                }
            }

            if (count % 40 == 0) {
                onProgress(0.5f, "Extracting: ${entry.name.takeLast(25)}")
            }

            entry = tarIn.nextTarEntry
        }
    }

    private fun createSymlink(linkFile: File, target: String) {
        linkFile.parentFile?.mkdirs()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val linkPath = linkFile.toPath()
                Files.deleteIfExists(linkPath)
                Files.createSymbolicLink(linkPath, Paths.get(target))
                return
            } catch (e: Exception) {
                Log.w(TAG, "Symlink creation failed for ${linkFile.name}: ${e.message}")
            }
        }
        try {
            val resolvedTarget = if (target.startsWith("/")) {
                File(rootfsDir, target.removePrefix("/"))
            } else {
                File(linkFile.parentFile, target)
            }
            if (resolvedTarget.exists() && resolvedTarget.isFile) {
                resolvedTarget.copyTo(linkFile, overwrite = true)
                linkFile.setReadable(true, false)
                linkFile.setWritable(true, false)
                linkFile.setExecutable(true, false)
            } else {
                linkFile.writeText(target)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fallback symlink write failed", e)
        }
    }

    fun ensurePermissions(file: File) {
        file.setReadable(true, false)
        file.setWritable(true, false)
        if (file.isDirectory) {
            file.setExecutable(true, false)
            file.listFiles()?.forEach { ensurePermissions(it) }
        } else {
            val parentName = file.parentFile?.name
            val absPath = file.absolutePath
            if (parentName == "bin" || parentName == "sbin" ||
                file.name == "sh" || file.name == "busybox" ||
                file.name == "n8n" || file.name == "node" || file.name == "npm" ||
                file.name.endsWith(".so") || file.name.contains("proot") ||
                absPath.contains("/bin/") || absPath.contains("/node_modules/.bin/")) {
                file.setExecutable(true, false)
            }
        }
    }
}
