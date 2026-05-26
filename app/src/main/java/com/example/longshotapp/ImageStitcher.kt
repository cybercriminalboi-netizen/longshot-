package com.example.longshotapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect

object ImageStitcher {
    fun stitch(bitmaps: List<Bitmap>): Bitmap? {
        if (bitmaps.isEmpty()) return null

        // 1. Crop out the static top (URL/Status bar) and bottom (Nav bar) from all frames
        val croppedBitmaps = bitmaps.map { bmp ->
            val topCrop = (bmp.height * 0.15).toInt()
            val bottomCrop = (bmp.height * 0.10).toInt()
            val newHeight = bmp.height - topCrop - bottomCrop
            Bitmap.createBitmap(bmp, 0, topCrop, bmp.width, newHeight)
        }

        if (croppedBitmaps.size == 1) return croppedBitmaps[0]

        var baseBitmap = croppedBitmaps[0]

        for (i in 1 until croppedBitmaps.size) {
            val nextBitmap = croppedBitmaps[i]
            val overlapRows = findVerticalOverlap(baseBitmap, nextBitmap)

            if (overlapRows > 0) {
                val uniqueHeight = nextBitmap.height - overlapRows
                if (uniqueHeight <= 0) continue

                val newHeight = baseBitmap.height + uniqueHeight
                val combinedBitmap = Bitmap.createBitmap(baseBitmap.width, newHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(combinedBitmap)

                canvas.drawBitmap(baseBitmap, 0f, 0f, null)

                val srcRect = Rect(0, overlapRows, nextBitmap.width, nextBitmap.height)
                val destRect = Rect(0, baseBitmap.height, baseBitmap.width, newHeight)
                canvas.drawBitmap(nextBitmap, srcRect, destRect, null)

                baseBitmap = combinedBitmap
            } else {
                val newHeight = baseBitmap.height + nextBitmap.height
                val combinedBitmap = Bitmap.createBitmap(baseBitmap.width, newHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(combinedBitmap)

                canvas.drawBitmap(baseBitmap, 0f, 0f, null)
                canvas.drawBitmap(nextBitmap, 0f, baseBitmap.height.toFloat(), null)

                baseBitmap = combinedBitmap
            }
        }
        return baseBitmap
    }

    private fun findVerticalOverlap(bitmapA: Bitmap, bitmapB: Bitmap): Int {
        val width = bitmapA.width
        val heightA = bitmapA.height
        val heightB = bitmapB.height
        val maxScanHeight = Math.min(heightA, heightB) / 2
        val samplePoints = (0 until width step 20).toList()

        val colorTolerance = 15 // Allow slight pixel shifts from compression

        for (overlap in maxScanHeight downTo 20) { 
            var match = true
            for (rowOffset in 0 until overlap step 2) { 
                val rowA = heightA - overlap + rowOffset
                val rowB = rowOffset

                for (col in samplePoints) {
                    val pixelA = bitmapA.getPixel(col, rowA)
                    val pixelB = bitmapB.getPixel(col, rowB)
                    
                    val rDiff = Math.abs(android.graphics.Color.red(pixelA) - android.graphics.Color.red(pixelB))
                    val gDiff = Math.abs(android.graphics.Color.green(pixelA) - android.graphics.Color.green(pixelB))
                    val bDiff = Math.abs(android.graphics.Color.blue(pixelA) - android.graphics.Color.blue(pixelB))

                    if (rDiff > colorTolerance || gDiff > colorTolerance || bDiff > colorTolerance) {
                        match = false
                        break
                    }
                }
                if (!match) break
            }
            if (match) return overlap
        }
        return 0
    }
}
