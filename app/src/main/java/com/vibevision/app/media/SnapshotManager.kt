package com.vibevision.app.media

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import com.vibevision.app.journal.ErrorJournal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Handles capturing and persisting processed optical frame buffer snapshots
 * to scoped storage (Pictures/VibeVision).
 */
object SnapshotManager {
    private const val TAG = "VibeSnapshotManager"

    suspend fun saveSnapshot(
        context: Context,
        bitmap: Bitmap,
        onComplete: (Uri?) -> Unit
    ) {
        withContext(Dispatchers.IO) {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "VIBEVISION_IMG_$timestamp.jpg"
            var savedUri: Uri? = null

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                        put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/VibeVision")
                        put(MediaStore.MediaColumns.IS_PENDING, 1)
                    }

                    val resolver = context.contentResolver
                    val imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                    if (imageUri != null) {
                        resolver.openOutputStream(imageUri)?.use { outputStream ->
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                        }

                        contentValues.clear()
                        contentValues.put(MediaStore.MediaColumns.IS_PENDING, 0)
                        resolver.update(imageUri, contentValues, null, null)
                        savedUri = imageUri
                    }
                } else {
                    // Legacy storage fallback (Android 9 and below)
                    @Suppress("DEPRECATION")
                    val picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    val vibeDir = File(picturesDir, "VibeVision")
                    if (!vibeDir.exists()) vibeDir.mkdirs()

                    val imageFile = File(vibeDir, filename)
                    FileOutputStream(imageFile).use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)
                    }

                    val values = ContentValues().apply {
                        put(MediaStore.Images.Media.DATA, imageFile.absolutePath)
                        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    }
                    savedUri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                }

                withContext(Dispatchers.Main) {
                    if (savedUri != null) {
                        Toast.makeText(context, "Snapshot saved to Gallery", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Failed to save snapshot", Toast.LENGTH_SHORT).show()
                    }
                    onComplete(savedUri)
                }
            } catch (e: Exception) {
                ErrorJournal.e(TAG, "Error saving processed snapshot: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Snapshot error: ${e.message}", Toast.LENGTH_SHORT).show()
                    onComplete(null)
                }
            }
        }
    }
}
