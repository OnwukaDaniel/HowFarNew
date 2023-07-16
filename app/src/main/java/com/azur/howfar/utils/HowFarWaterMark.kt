package com.azur.howfar.utils

import android.graphics.*


object HowFarWaterMark {
    fun mark(src: Bitmap, watermark: String, location: Point, color: Int = Color.BLACK, alpha: Int = 50, size: Float = 18F, underline: Boolean = false):
            Bitmap? {
        val w = src.width
        val h = src.height
        val result = Bitmap.createBitmap(w, h, src.config)
        val canvas = Canvas(result)
        canvas.drawBitmap(src, 0F, 0F, null)
        val paint = Paint()
        paint.color = color
        paint.alpha = alpha
        paint.textSize = size
        paint.isAntiAlias = true
        paint.isUnderlineText = underline
        canvas.drawText(watermark, location.x.toFloat(), location.y.toFloat(), paint)
        return result
    }

    /**
     * Embeds an image watermark over a source image to produce
     * a watermarked one.
     * @param source The source image where watermark should be placed
     * @param watermark Watermark image to place
     * @param ratio A float value < 1 to give the ratio of watermark's height to image's height,
     * try changing this from 0.20 to 0.60 to obtain right results
     */
    fun addWatermark(source: Bitmap, watermark: Bitmap, ratio: Float): Bitmap? {
        val canvas: Canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG or Paint.FILTER_BITMAP_FLAG)
        val bmp: Bitmap
        val matrix: Matrix
        val r: RectF
        val width: Int
        val height: Int
        val scale: Float
        width = source.width
        height = source.height

        // Create the new bitmap
        bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

        // Copy the original bitmap into the new one
        canvas = Canvas(bmp)
        canvas.drawBitmap(source, 0F, 0F, paint)

        // Scale the watermark to be approximately to the ratio given of the source image height
        scale = (height.toFloat() * ratio / watermark.height.toFloat())

        // Create the matrix
        matrix = Matrix()
        matrix.postScale(scale, scale)

        // Determine the post-scaled size of the watermark
        r = RectF(0F, 0F, watermark.width.toFloat(), watermark.height.toFloat())
        matrix.mapRect(r)

        // Move the watermark to the bottom right corner
        matrix.postTranslate(width - r.width(), height - r.height())

        // Draw the watermark
        canvas.drawBitmap(watermark, matrix, paint)
        return bmp
    }
}