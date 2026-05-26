package com.example.longshotapp

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent

class LongshotAccessibilityService : AccessibilityService() {
    companion object {
        var instance: LongshotAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    fun autoScrollDown(callback: () -> Unit) {
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        val screenWidth = displayMetrics.widthPixels

        // Simulate a swipe from 70% down the screen up to 30% to scroll down
        val startY = (screenHeight * 0.7f)
        val endY = (screenHeight * 0.3f)
        val centerX = (screenWidth / 2f)

        val swipePath = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }

        val gestureBuilder = GestureDescription.Builder()
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(swipePath, 0, 400))

        dispatchGesture(gestureBuilder.build(), object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                // Wait 600ms for the scrolling momentum to completely stop before capturing
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    callback()
                }, 600)
            }
        }, null)
    }
}
