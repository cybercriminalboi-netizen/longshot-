package com.example.longshotapp

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val OVERLAY_PERMISSION_REQ_CODE = 1001
    private val MEDIA_PROJECTION_REQ_CODE = 1002
    private lateinit var mediaProjectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        findViewById<Button>(R.id.btn_start).setOnClickListener {
            checkAndStartService()
        }
    }

    private fun checkAndStartService() {
        // 1. Check Overlay Permission (Draw over other apps)
        if (!Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ_CODE)
        } else {
            // 2. Overlay permission granted, now request Screen Capture permission
            requestScreenCapturePermission()
        }
    }

    private fun requestScreenCapturePermission() {
        startActivityForResult(
            mediaProjectionManager.createScreenCaptureIntent(),
            MEDIA_PROJECTION_REQ_CODE
        )
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == OVERLAY_PERMISSION_REQ_CODE) {
            if (Settings.canDrawOverlays(this)) {
                requestScreenCapturePermission()
            } else {
                Toast.makeText(this, "Overlay permission is required!", Toast.LENGTH_SHORT).show()
            }
        }

        if (requestCode == MEDIA_PROJECTION_REQ_CODE) {
            if (resultCode == RESULT_OK && data != null) {
                // 3. Both permissions granted! Launch the background service and pass the capture intent data
                val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                    putExtra("RESULT_CODE", resultCode)
                    putExtra("DATA_INTENT", data)
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
                // Minimize the app so the user can see their target screen
                moveTaskToBack(true)
            } else {
                Toast.makeText(this, "Screen capture permission denied!", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
