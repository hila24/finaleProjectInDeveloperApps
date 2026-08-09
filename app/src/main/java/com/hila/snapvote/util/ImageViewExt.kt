package com.hila.snapvote.util

import android.widget.ImageView
import coil.load
import com.hila.snapvote.R
import java.nio.ByteBuffer

/**
 * Images arrive from Firestore as Base64 text. Coil can render a ByteBuffer, so the
 * decoding still happens off the main thread and the result stays in Coil's cache.
 */
fun ImageView.loadBase64(data: String?, cacheKey: String? = null) {
    if (data.isNullOrEmpty()) {
        setImageResource(R.drawable.ic_image_placeholder)
        return
    }
    val bytes = runCatching { ImageCompressor.fromBase64(data) }.getOrNull()
    if (bytes == null) {
        setImageResource(R.drawable.ic_image_placeholder)
        return
    }
    load(ByteBuffer.wrap(bytes)) {
        crossfade(true)
        placeholder(R.drawable.ic_image_placeholder)
        error(R.drawable.ic_image_placeholder)
        if (cacheKey != null) memoryCacheKey(cacheKey)
    }
}
