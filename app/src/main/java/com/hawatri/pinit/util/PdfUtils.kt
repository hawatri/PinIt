package com.hawatri.pinit.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.core.net.toUri

object PdfUtils {

    /**
     * Render the first page of a PDF as a Bitmap. Returns null on any failure
     * (URI revoked, file deleted, password-protected PDF, etc.).
     *
     * Width/height are upper bounds — the bitmap keeps the original aspect.
     */
    fun renderFirstPage(context: Context, uri: Uri, maxWidth: Int = 512, maxHeight: Int = 512): Bitmap? {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        return try {
            pfd = context.contentResolver.openFileDescriptor(uri, "r") ?: return null
            renderer = PdfRenderer(pfd)
            if (renderer.pageCount <= 0) return null
            renderer.openPage(0).use { page ->
                val ratio = minOf(
                    maxWidth.toFloat() / page.width,
                    maxHeight.toFloat() / page.height
                ).coerceAtMost(1f)
                val w = (page.width * ratio).toInt().coerceAtLeast(1)
                val h = (page.height * ratio).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                // White background — many PDFs render transparent backgrounds
                Canvas(bitmap).drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        } catch (e: Exception) {
            null
        } finally {
            try { renderer?.close() } catch (e: Exception) {}
            try { pfd?.close() } catch (e: Exception) {}
        }
    }
}

object ImageUriUtils {

    /** Best-effort decode of an image URI down to a maxDimension box (preserves aspect). */
    fun decodeBitmap(context: Context, uriString: String, maxDimension: Int = 1024): Bitmap? {
        val uri = try { uriString.toUri() } catch (e: Exception) { return null }
        return decodeBitmap(context, uri, maxDimension)
    }

    fun decodeBitmap(context: Context, uri: Uri, maxDimension: Int = 1024): Bitmap? {
        return try {
            // First pass — bounds only
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, bounds)
            }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sample = 1
            while (bounds.outWidth / sample > maxDimension || bounds.outHeight / sample > maxDimension) {
                sample *= 2
            }

            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, opts)
            }
        } catch (e: Exception) {
            null
        }
    }
}
