package com.example.longshotapp

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect

object ImageStitcher {

    /**
     * Stitches a list of captured screenshots vertically by detecting overlapping regions.
     */
    fun stitch(bitmaps: List<Bitmap>): Bitmap? {
        if (bitmaps.isEmpty()) return null
        if (bitmaps.size == 1) return bitmaps[0]

        var baseBitmap = bitmaps[0]

        for (i in 1 until bitmaps.size) {
            val nextBitmap = bitmaps[i]
            val overlapRows = findVerticalOverlap(baseBitmap, nextBitmap)

            if (overlapRows > 0) {
                // Combine the base bitmap with the unique part of the next bitmap
                val uniqueHeight = nextBitmap.height - overlapRows
                if (uniqueHeight <= 0) continue // Completely identical frame, skip it

                val newHeight = baseBitmap.height + uniqueHeight
                val combinedBitmap = Bitmap.createBitmap(baseBitmap.width, newHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(combinedBitmap)

                // Draw previous accumulated image
                canvas.drawBitmap(baseBitmap, 0f, 0f, null)

                // Draw only the non-overlapping portion of the new image at the bottom
                val srcRect = Rect(0, overlapRows, nextBitmap.width, nextBitmap.height)
                val destRect = Rect(0, baseBitmap.height, baseBitmap.width, newHeight)
                canvas.drawBitmap(nextBitmap, srcRect, destRect, null)

                baseBitmap = combinedBitmap
            } else {
                // If no overlap is detected (user scrolled too fast), append them cleanly
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

    /**
     * Compares the bottom rows of bitmapA with the top rows of bitmapB 
     * to find the number of overlapping horizontal rows of pixels.
     */
    private fun findVerticalOverlap(bitmapA: Bitmap, bitmapB: Bitmap): Int {
        val width = bitmapA.width
        val heightA = bitmapA.height
        val heightB = bitmapB.height

        // To save memory and performance, scan a max of half the screen height for matching areas
        val maxScanHeight = Math.min(heightA, heightB) / 2
        
        // Sample columns across the screen width to speed up row-by-row comparisons (e.g., check every 20th pixel)
        val samplePoints = (0 until width step 20).toList()

        // Scan row by row backwards from the bottom of bitmapA
        for (overlap in maxScanHeight downTo 20) { 
            var match = true
            
            // Compare row elements
            for (rowOffset in 0 until overlap) {
                val rowA = heightA - overlap + rowOffset
                val rowB = rowOffset

                for (col in samplePoints) {
                    if (bitmapA.getPixel(col, rowA) != bitmapB.getPixel(col, rowB)) {
                        match = false
                        break
                    }
                }
                if (!match) break
            }

            if (match) {
                return overlap // Found the match! Return how many rows overlap.
            }
        }

        return 0 // No match found
    }
}
