package com.simpleaibrowser.sab.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

/** Renders a URL/text into a scannable QR code bitmap (zxing core, offline, no network). */
object SabQrCode {

    fun encode(text: String, sizePx: Int = 760): Bitmap? {
        if (text.isBlank()) return null
        return runCatching {
            val sanitized = if (sizePx > 1200) 1200 else sizePx
            val hints = mapOf(
                EncodeHintType.MARGIN to 2,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sanitized, sanitized, hints)
            val width = matrix.width
            val height = matrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                val rowOffset = y * width
                for (x in 0 until width) {
                    pixels[rowOffset + x] = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
                }
            }
            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        }.getOrNull()
    }
}
