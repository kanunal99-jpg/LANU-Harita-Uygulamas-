package com.example.haritalar.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint

/**
 * Creates deterministic weather marker bitmaps for MapLibre style images.
 * Kept as a public top-level helper so MapLibreContainer can register the
 * weather icons without coupling map rendering to a remote icon service.
 */
fun createWeatherBitmap(context: Context, glyph: String): Bitmap {
    val density = context.resources.displayMetrics.density
    val size = (48f * density).toInt().coerceAtLeast(64)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f

    Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = Color.argb(55, 0, 0, 0)
        canvas.drawCircle(center, center + 2f * density, size * 0.39f, it)
    }
    Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = Color.WHITE
        canvas.drawCircle(center, center, size * 0.39f, it)
    }
    Paint(Paint.ANTI_ALIAS_FLAG).also {
        it.color = Color.parseColor("#2563EB")
        it.textAlign = Paint.Align.CENTER
        it.textSize = size * 0.43f
        it.isFakeBoldText = true
        canvas.drawText(glyph, center, center - (it.ascent() + it.descent()) / 2f, it)
    }
    return bitmap
}
