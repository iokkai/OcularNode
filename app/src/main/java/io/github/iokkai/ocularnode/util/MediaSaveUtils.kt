package io.github.iokkai.ocularnode.util

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

/**
 * 通用多媒體存檔工具 (MediaSaveUtils)
 * 支援 Android 8 ~ Android 15+ (API 26~36) 之安全 Scoped Storage 與 MediaStore API 存檔。
 */
object MediaSaveUtils {

    /**
     * 將 Bitmap 儲存至系統相片相簿 (Pictures / OcularNode)
     */
    suspend fun saveImageToGallery(
        context: Context,
        bitmap: Bitmap,
        baseFileName: String = "OcularNode_${System.currentTimeMillis()}"
    ): Result<Uri?> = withContext(Dispatchers.IO) {
        try {
            val fileName = if (baseFileName.endsWith(".jpg", ignoreCase = true)) baseFileName else "$baseFileName.jpg"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/OcularNode")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext Result.failure(Exception("Failed to create MediaStore entry for image"))

                resolver.openOutputStream(uri)?.use { outputStream: OutputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                    outputStream.flush()
                }

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                Result.success(uri)
            } else {
                val picturesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                    "OcularNode"
                ).apply { mkdirs() }
                val targetFile = File(picturesDir, fileName)

                FileOutputStream(targetFile).use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                    outputStream.flush()
                }

                val uri = Uri.fromFile(targetFile)
                Result.success(uri)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 將影片檔案儲存至系統影片相簿 (Movies / OcularNode)
     */
    suspend fun saveVideoToGallery(
        context: Context,
        sourceVideoFile: File,
        baseFileName: String = "OcularNode_Video_${System.currentTimeMillis()}"
    ): Result<Uri?> = withContext(Dispatchers.IO) {
        try {
            if (!sourceVideoFile.exists() || sourceVideoFile.length() == 0L) {
                return@withContext Result.failure(IllegalArgumentException("Source video file does not exist or is empty"))
            }

            val fileName = if (baseFileName.endsWith(".mp4", ignoreCase = true)) baseFileName else "$baseFileName.mp4"

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/OcularNode")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }

                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
                    ?: return@withContext Result.failure(Exception("Failed to create MediaStore entry for video"))

                resolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(sourceVideoFile).use { inputStream ->
                        inputStream.copyTo(outputStream)
                    }
                    outputStream.flush()
                }

                contentValues.clear()
                contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)

                Result.success(uri)
            } else {
                val moviesDir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
                    "OcularNode"
                ).apply { mkdirs() }
                val targetFile = File(moviesDir, fileName)

                sourceVideoFile.copyTo(targetFile, overwrite = true)
                val uri = Uri.fromFile(targetFile)
                Result.success(uri)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
