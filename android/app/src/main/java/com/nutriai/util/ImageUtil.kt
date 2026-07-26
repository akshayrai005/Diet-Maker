package com.nutriai.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

/** Image helpers: downscale + JPEG-compress for upload, and a private on-device progress gallery. */
object ImageUtil {

    /** Reads a picked/captured image, downscales it and returns compressed JPEG bytes. */
    fun downscaledJpegBytes(context: Context, uri: Uri, maxDim: Int = 768, quality: Int = 72): ByteArray? {
        return try {
            val bmp = context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) return null
                BitmapFactory.decodeStream(input)
            } ?: return null
            compress(scaleDown(bmp, maxDim), quality)
        } catch (_: Exception) {
            null
        }
    }

    fun toBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun scaleDown(bmp: Bitmap, maxDim: Int): Bitmap {
        val longest = maxOf(bmp.width, bmp.height)
        if (longest <= maxDim) return bmp
        val scale = maxDim.toFloat() / longest
        return Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true)
    }

    private fun compress(bmp: Bitmap, quality: Int): ByteArray {
        val baos = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, baos)
        return baos.toByteArray()
    }

    /** Deletes any photos saved by older versions — we no longer store body photos. */
    fun clearProgress(context: Context) {
        runCatching {
            File(context.filesDir, "progress").deleteRecursively()
            context.cacheDir.listFiles { f -> f.name.startsWith("capture_") }?.forEach { it.delete() }
        }
    }

    /** A cache file + FileProvider uri for the camera to write a full-res capture into. */
    fun newCameraOutput(context: Context, timestamp: Long): Pair<Uri, File> {
        val file = File(context.cacheDir, "capture_$timestamp.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        return uri to file
    }

    // -----------------------------------------------------------------------
    // Progress-photo gallery — physique tracking. Images stay in app-private
    // internal storage and are NEVER uploaded; only a filename (localRef) is
    // recorded on the server. Kept in its own folder ("body_progress") so it is
    // NOT touched by clearProgress(), which wipes the legacy "progress" folder.
    // -----------------------------------------------------------------------
    private const val BODY_PROGRESS_DIR = "body_progress"

    fun progressPhotoDir(context: Context): File =
        File(context.filesDir, BODY_PROGRESS_DIR).apply { if (!exists()) mkdirs() }

    /** The on-device file for a given localRef (may or may not exist on this device). */
    fun progressPhotoFile(context: Context, localRef: String): File =
        File(progressPhotoDir(context), localRef)

    /**
     * Copies a picked/captured image into app-private storage (downscaled JPEG) and returns its
     * localRef filename ("<uuid>.jpg"), or null if it couldn't be read/written.
     */
    fun saveProgressPhoto(context: Context, uri: Uri): String? {
        val bytes = downscaledJpegBytes(context, uri, maxDim = 1080, quality = 80) ?: return null
        val name = "${java.util.UUID.randomUUID()}.jpg"
        return try {
            File(progressPhotoDir(context), name).outputStream().use { it.write(bytes) }
            name
        } catch (_: Exception) {
            null
        }
    }

    /** Removes the on-device image for a localRef. No-op if it isn't present. */
    fun deleteProgressPhoto(context: Context, localRef: String) {
        runCatching { progressPhotoFile(context, localRef).delete() }
    }
}
