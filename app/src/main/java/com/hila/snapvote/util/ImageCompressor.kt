package com.hila.snapvote.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Turns a picked photo into two JPEGs that are small enough to live inside Firestore:
 * a full-size one (its own document, under the 1 MB document limit even after Base64)
 * and a thumbnail that is stored on the poll document itself so the feed needs no
 * extra reads.
 */
object ImageCompressor {

    private const val FULL_MAX_SIDE = 1080
    private const val THUMB_MAX_SIDE = 320

    /** Base64 grows by 4/3, so 700 KB of JPEG stays comfortably under the 1 MB limit. */
    private const val MAX_FULL_BYTES = 700_000

    fun fullImage(context: Context, uri: Uri): ByteArray {
        val bitmap = decodeScaled(context, uri, FULL_MAX_SIDE)
        var quality = 80
        var bytes = bitmap.toJpeg(quality)
        while (bytes.size > MAX_FULL_BYTES && quality > 40) {
            quality -= 15
            bytes = bitmap.toJpeg(quality)
        }
        if (bytes.size > MAX_FULL_BYTES) {
            bytes = bitmap.scaleTo(720).toJpeg(60)
        }
        return bytes
    }

    fun thumbnail(context: Context, uri: Uri): ByteArray =
        decodeScaled(context, uri, THUMB_MAX_SIDE).toJpeg(62)

    fun toBase64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    fun fromBase64(value: String): ByteArray = Base64.decode(value, Base64.NO_WRAP)

    // ------------------------------------------------------------------ decoding

    private fun decodeScaled(context: Context, uri: Uri, maxSide: Int): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, bounds)
        }

        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxSide)
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: error("Could not read the selected image")

        return applyExifRotation(context, uri, decoded).scaleTo(maxSide)
    }

    private fun sampleSizeFor(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        var longest = max(width, height)
        while (longest / 2 >= maxSide * 2) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    private fun Bitmap.scaleTo(maxSide: Int): Bitmap {
        val longest = max(width, height)
        if (longest <= maxSide) return this
        val ratio = maxSide.toFloat() / longest
        return Bitmap.createScaledBitmap(this, (width * ratio).toInt(), (height * ratio).toInt(), true)
    }

    private fun Bitmap.toJpeg(quality: Int): ByteArray =
        ByteArrayOutputStream().use { out ->
            compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }

    /** Camera photos carry their orientation in EXIF – apply it so nothing shows sideways. */
    private fun applyExifRotation(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL

        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
