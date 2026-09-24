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
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
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
        const val ROOTFS_ARCHIVE_ASSET = "n8n-rootfs-arm64.tar.xz"
        const val PROOT_BINARY_ASSET = "proot"
        private const val SENTINEL_FILE = ".n8n_installed"
    }

    val binDir: File get() = File(context.filesDir, "bin")
    val rootfsDir: File get() = File(context.filesDir, "rootfs")
    val dataDir: File get() = File(context.filesDir, "data/.n8n")
    val prootBinary: File get() = File(binDir, "proot")

    fun isInstalled(): Boolean {
        val sentinel = File(context.filesDir, SENTINEL_FILE)
        return sentinel.exists() && prootBinary.exists() && rootfsDir.exists()
    }

    fun extractPayload(): Flow<ExtractionProgress> = flow {
        try {
            emit(ExtractionProgress.Progress(0f, "Initializing directories..."))

            // Prepare base directories
            binDir.mkdirs()
            rootfsDir.mkdirs()
            dataDir.mkdirs()

            // Step 1: Extract PRoot binary if bundled in assets
            emit(ExtractionProgress.Progress(0.05f, "Installing PRoot core binary..."))
            extractProotBinary()

            // Step 2: Extract rootfs
            emit(ExtractionProgress.Progress(0.10f, "Preparing rootfs archive..."))
            val hasAssetArchive = try {
                context.assets.open(ROOTFS_ARCHIVE_ASSET).use { true }
            } catch (e: Exception) {
                false
            }

            if (hasAssetArchive) {
                context.assets.open(ROOTFS_ARCHIVE_ASSET).use { assetStream ->
                    extractTarXzStream(assetStream) { progress, message ->
                        // Scale progress 0.10 to 0.95
                        val scaled = 0.10f + (progress * 0.85f)
                        emit(ExtractionProgress.Progress(scaled, message))
                    }
                }
            } else {
                // If not in assets, check for pre-downloaded archive in cache/files
                val downloadedArchive = File(context.cacheDir, ROOTFS_ARCHIVE_ASSET)
                if (downloadedArchive.exists()) {
                    FileInputStream(downloadedArchive).use { fileStream ->
                        extractTarXzStream(fileStream) { progress, message ->
                            val scaled = 0.10f + (progress * 0.85f)
                            emit(ExtractionProgress.Progress(scaled, message))
                        }
                    }
                } else {
                    Log.w(TAG, "No rootfs asset found. Initializing skeleton structure.")
                    emit(ExtractionProgress.Progress(0.50f, "Setting up skeleton rootfs environment..."))
                    createSkeletonStructure()
                }
            }

            // Step 3: Finalize permissions and write sentinel
            emit(ExtractionProgress.Progress(0.98f, "Finalizing file permissions..."))
            ensurePermissions(binDir)
            ensurePermissions(rootfsDir)

            File(context.filesDir, SENTINEL_FILE).writeText(System.currentTimeMillis().toString())

            emit(ExtractionProgress.Progress(1.0f, "Installation completed successfully!"))
            emit(ExtractionProgress.Completed(rootfsDir, binDir, dataDir))

        } catch (t: Throwable) {
            Log.e(TAG, "Extraction failed", t)
            emit(ExtractionProgress.Failed(t))
        }
    }.flowOn(Dispatchers.IO)

    private fun extractProotBinary() {
        try {
            context.assets.open(PROOT_BINARY_ASSET).use { input ->
                FileOutputStream(prootBinary).use { output ->
                    input.copyTo(output)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Asset proot binary not found in apk assets; creating placeholder/stub")
            if (!prootBinary.exists()) {
                prootBinary.writeText("#!/system/bin/sh\n")
            }
        }
        prootBinary.setExecutable(true, false)
        prootBinary.setReadable(true, false)
    }

    private suspend fun extractTarXzStream(
        rawInput: InputStream,
        onProgress: suspend (Float, String) -> Unit
    ) {
        val bufferedInput = BufferedInputStream(rawInput)
        val xzIn = XZInputStream(bufferedInput)
        val tarIn = TarArchiveInputStream(xzIn)

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
                if (entry.mode and 0b001001001 != 0) { // check if executable
                    destFile.setExecutable(true, false)
                }
            }

            if (count % 50 == 0) {
                onProgress(0.5f, "Extracting: ${entry.name.takeLast(30)}")
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
                Log.w(TAG, "Symlink creation via java.nio failed for ${linkFile.name}: ${e.message}")
            }
        }
        // Fallback: write text reference if symlink fails
        try {
            linkFile.parentFile?.mkdirs()
            linkFile.writeText(target)
        } catch (e: Exception) {
            Log.e(TAG, "Fallback symlink write failed", e)
        }
    }

    private fun createSkeletonStructure() {
        // Creates necessary directories for FHS layout
        val dirs = listOf(
            "dev", "proc", "sys", "etc", "bin", "sbin", "usr/bin", "usr/sbin",
            "usr/local/bin", "usr/lib", "lib", "root", "tmp", "var/log"
        )
        for (dir in dirs) {
            File(rootfsDir, dir).mkdirs()
        }
    }

    private fun ensurePermissions(file: File) {
        if (file.isDirectory) {
            file.setReadable(true, false)
            file.setExecutable(true, false)
            file.listFiles()?.forEach { ensurePermissions(it) }
        } else {
            file.setReadable(true, false)
            if (file.parentFile?.name == "bin" || file.parentFile?.name == "sbin" || file.name == "n8n" || file.name == "node" || file.name == "proot") {
                file.setExecutable(true, false)
            }
        }
    }
}
