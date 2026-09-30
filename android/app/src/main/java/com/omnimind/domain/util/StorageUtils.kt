package com.omnimind.domain.util

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.OpenableColumns
import com.omnimind.native.LlamaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

object StorageUtils {

    fun getModelsDirectory(context: Context): File {
        val dir = File(context.getExternalFilesDir(null), "models")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getAvailableDiskSpace(context: Context): Long {
        val dir = getModelsDirectory(context)
        val stat = StatFs(dir.absolutePath)
        return stat.availableBlocksLong * stat.blockSizeLong
    }

    fun getTotalDiskSpace(context: Context): Long {
        val dir = getModelsDirectory(context)
        val stat = StatFs(dir.absolutePath)
        return stat.blockCountLong * stat.blockSizeLong
    }

    fun getFileNameFromUri(context: Context, uri: Uri): String {
        var name = "model.gguf"
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1 && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } else if (uri.path != null) {
            name = File(uri.path!!).name
        }
        return name
    }

    fun getFileSizeFromUri(context: Context, uri: Uri): Long {
        var size = 0L
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1 && cursor.moveToFirst()) {
                    size = cursor.getLong(sizeIndex)
                }
            }
        } else if (uri.path != null) {
            size = File(uri.path!!).length()
        }
        return size
    }

    suspend fun computeSha256(file: File, cancelFlag: AtomicBoolean? = null): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024) // 64 KB chunk
        FileInputStream(file).use { input ->
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                if (cancelFlag?.get() == true) {
                    throw InterruptedException("SHA-256 computation cancelled")
                }
                digest.update(buffer, 0, bytesRead)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun copyUriToModelsDir(
        context: Context,
        uri: Uri,
        destFileName: String,
        cancelFlag: AtomicBoolean? = null,
        onProgress: ((bytesCopied: Long, totalBytes: Long) -> Unit)? = null
    ): File = withContext(Dispatchers.IO) {
        val modelsDir = getModelsDirectory(context)
        val tempDestFile = File(modelsDir, "$destFileName.part")
        val finalDestFile = File(modelsDir, destFileName)

        val totalSize = getFileSizeFromUri(context, uri)
        val freeSpace = getAvailableDiskSpace(context)

        // Validate free disk space with a buffer (100 MB headroom)
        val requiredSpace = if (totalSize > 0) totalSize + 100 * 1024 * 1024L else 500 * 1024 * 1024L
        if (freeSpace < requiredSpace) {
            throw IllegalStateException(
                "Insufficient free storage. Available: ${freeSpace / (1024 * 1024)} MB, Required: ${requiredSpace / (1024 * 1024)} MB"
            )
        }

        if (tempDestFile.exists()) {
            tempDestFile.delete()
        }

        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempDestFile).use { output ->
                val buffer = ByteArray(128 * 1024) // 128 KB chunk for efficient streaming
                var bytesRead: Int
                var totalCopied = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    if (cancelFlag?.get() == true) {
                        tempDestFile.delete()
                        throw InterruptedException("File import cancelled")
                    }
                    output.write(buffer, 0, bytesRead)
                    totalCopied += bytesRead
                    onProgress?.invoke(totalCopied, totalSize)
                }
                output.flush()
            }
        } ?: throw IllegalStateException("Could not open input stream for URI: $uri")

        // Atomic rename from .part to final
        if (finalDestFile.exists()) {
            finalDestFile.delete()
        }
        if (!tempDestFile.renameTo(finalDestFile)) {
            tempDestFile.delete()
            throw IllegalStateException("Failed to move temporary import file into place")
        }

        // Validate GGUF format
        if (!LlamaEngine.validateGguf(finalDestFile.absolutePath)) {
            finalDestFile.delete()
            throw IllegalArgumentException("The selected file is not a valid GGUF model")
        }

        finalDestFile
    }

    fun formatBytes(bytes: Long): String {
        val gb = bytes.toDouble() / (1024 * 1024 * 1024)
        if (gb >= 1.0) {
            return String.format("%.2f GB", gb)
        }
        val mb = bytes.toDouble() / (1024 * 1024)
        if (mb >= 1.0) {
            return String.format("%.1f MB", mb)
        }
        val kb = bytes.toDouble() / 1024
        return String.format("%.0f KB", kb)
    }
}
