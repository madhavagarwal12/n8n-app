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
        val shBinary = File(rootfsDir, "bin/sh")
        return sentinel.exists() && shBinary.exists() && prootBinary.exists()
    }

    fun extractPayload(): Flow<ExtractionProgress> = flow {
        try {
            emit(ExtractionProgress.Progress(0.02f, "Preparing file system sandbox..."))

            binDir.mkdirs()
            rootfsDir.mkdirs()
            dataDir.mkdirs()

            // Step 1: Check if rootfs asset is bundled
            var assetName: String? = null
            try {
                if (context.assets.list("")?.contains(ROOTFS_XZ_ASSET) == true) {
                    assetName = ROOTFS_XZ_ASSET
                } else if (context.assets.list("")?.contains(ROOTFS_GZ_ASSET) == true) {
                    assetName = ROOTFS_GZ_ASSET
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error checking assets", e)
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

            // Configure resolv.conf inside rootfs
            val etcDir = File(rootfsDir, "etc").apply { mkdirs() }
            File(etcDir, "resolv.conf").writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n")

            // Step 2: Finalize permissions
            emit(ExtractionProgress.Progress(0.96f, "Configuring POSIX permissions..."))
            ensurePermissions(rootfsDir)

            // Validate that /bin/sh exists
            val shBinary = File(rootfsDir, "bin/sh")
            if (!shBinary.exists()) {
                throw IllegalStateException("Rootfs extraction incomplete: /bin/sh not found in ${rootfsDir.absolutePath}")
            }
            shBinary.setExecutable(true, false)

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
            val destFile = File(rootfsDir, entry.name)

            if (entry.isDirectory) {
                destFile.mkdirs()
            } else if (entry.isSymbolicLink) {
                createSymlink(destFile, entry.linkName)
            } else {
                destFile.parentFile?.mkdirs()
                FileOutputStream(destFile).use { output ->
                    tarIn.copyTo(output)
                }
                if (entry.mode and 0b001001001 != 0) {
                    destFile.setExecutable(true, false)
                }
            }

            if (count % 40 == 0) {
                onProgress(0.5f, "Extracting: ${entry.name.takeLast(25)}")
            }

            entry = tarIn.nextTarEntry
        }
    }

    private fun createSymlink(linkFile: File, target: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val linkPath = Paths.get(linkFile.absolutePath)
                val targetPath = Paths.get(target)
                Files.deleteIfExists(linkPath)
                Files.createSymbolicLink(linkPath, targetPath)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Symlink creation failed for ${linkFile.name}: ${e.message}")
            }
        }
        try {
            linkFile.parentFile?.mkdirs()
            linkFile.writeText(target)
        } catch (e: Exception) {
            Log.e(TAG, "Fallback symlink write failed", e)
        }
    }

    private fun ensurePermissions(file: File) {
        if (file.isDirectory) {
            file.setReadable(true, false)
            file.setExecutable(true, false)
            file.listFiles()?.forEach { ensurePermissions(it) }
        } else {
            file.setReadable(true, false)
            val parentName = file.parentFile?.name
            if (parentName == "bin" || parentName == "sbin" || file.name == "sh" || file.name == "n8n" || file.name == "node") {
                file.setExecutable(true, false)
            }
        }
    }
}
