package com.example.longshotapp

import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import java.io.OutputStream

class ScreenCaptureService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams
    
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    private val capturedBitmaps = ArrayList<Bitmap>()
    
    private var screenWidth = 0
    private var screenHeight = 0
    private var screenDensity = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val windowMetrics = windowManager.currentWindowMetrics
            val bounds = windowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
            screenDensity = resources.configuration.densityDpi
        } else {
            val displayMetrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            screenWidth = displayMetrics.widthPixels
            screenHeight = displayMetrics.heightPixels
            screenDensity = displayMetrics.densityDpi
        }

        startForegroundServiceNotification()
        createFloatingWidget()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val dataIntent = intent?.getParcelableExtra<Intent>("DATA_INTENT")

        if (resultCode == Activity.RESULT_OK && dataIntent != null) {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mpManager.getMediaProjection(resultCode, dataIntent)
            initCaptureEngine()
        } else {
            Toast.makeText(this, "Failed to initialize Capture Engine", Toast.LENGTH_SHORT).show()
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun initCaptureEngine() {
        try {
            imageReader = ImageReader.newInstance(screenWidth, screenHeight, PixelFormat.RGBA_8888, 2)
            
            mediaProjection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() {
                    cleanUpEngine()
                }
            }, Handler(Looper.getMainLooper()))

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "LongshotDisplay", screenWidth, screenHeight, screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader?.surface, null, null
            )
        } catch (e: Exception) {
            Toast.makeText(this, "Engine Init Failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startForegroundServiceNotification() {
        val channelId = "longshot_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Longshot Capture", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Longshot Manual Mode")
            .setContentText("Tap widget to capture segment. Hold to finish.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        startForeground(1, notification)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createFloatingWidget() {
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_widget, null)

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        params.gravity = Gravity.TOP or Gravity.START
        params.x = 100
        params.y = 100

        windowManager.addView(floatingView, params)

        val buttonText = floatingView.findViewById<TextView>(R.id.button_text)
        buttonText.text = "START"

        var isFirstCapture = true

        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (isFirstCapture) {
                    // First tap: Just capture the initial screen
                    captureSingleFrame {
                        buttonText.text = "SCROLL"
                        isFirstCapture = false
                    }
                } else {
                    // Subsequent taps: Hide, Auto-Scroll, Wait, Capture, Show
                    floatingView.visibility = View.INVISIBLE
                    val scroller = LongshotAccessibilityService.instance
                    
                    if (scroller != null) {
                        scroller.autoScrollDown {
                            captureSingleFrame {
                                floatingView.visibility = View.VISIBLE
                            }
                        }
                    } else {
                        floatingView.visibility = View.VISIBLE
                        Toast.makeText(applicationContext, "Please enable Accessibility Permission!", Toast.LENGTH_LONG).show()
                    }
                }
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                buttonText.text = "STITCHING..."
                processAndStitchImages()
            }
        })

        floatingView.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                if (gestureDetector.onTouchEvent(event)) return true

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - initialTouchX).toInt()
                        val deltaY = (event.rawY - initialTouchY).toInt()
                        if (Math.abs(deltaX) > 15 || Math.abs(deltaY) > 15) {
                            params.x = initialX + deltaX
                            params.y = initialY + deltaY
                            windowManager.updateViewLayout(floatingView, params)
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun captureSingleFrame(onCaptureComplete: () -> Unit) {
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                imageReader?.acquireLatestImage()?.use { image ->
                    val planes = image.planes
                    val buffer = planes[0].buffer
                    val pixelStride = planes[0].pixelStride
                    val rowStride = planes[0].rowStride
                    val rowPadding = rowStride - pixelStride * screenWidth

                    val bitmap = Bitmap.createBitmap(
                        screenWidth + rowPadding / pixelStride,
                        screenHeight, Bitmap.Config.ARGB_8888
                    )
                    bitmap.copyPixelsFromBuffer(buffer)
                    
                    val cleanBitmap = Bitmap.createBitmap(bitmap, 0, 0, screenWidth, screenHeight)
                    capturedBitmaps.add(cleanBitmap)
                    
                    Toast.makeText(this, "Frame ${capturedBitmaps.size} Captured", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Capture missed, try again!", Toast.LENGTH_SHORT).show()
            } finally {
                onCaptureComplete()
            }
        }, 150) // Tiny delay to ensure button is fully hidden before taking the shot
    }

    private fun processAndStitchImages() {
        if (capturedBitmaps.isEmpty()) {
            Toast.makeText(this, "No frames captured!", Toast.LENGTH_SHORT).show()
            stopSelf()
            return
        }

        val stitchedBitmap = ImageStitcher.stitch(capturedBitmaps)

        if (stitchedBitmap != null) {
            saveBitmapToStorage(stitchedBitmap)
        } else {
            Toast.makeText(this, "Stitching failed!", Toast.LENGTH_SHORT).show()
        }
        
        // This stops the service, destroys the capture engine, and removes the button completely
        stopSelf()
    }

    private fun saveBitmapToStorage(bitmap: Bitmap) {
        val filename = "Longshot_${System.currentTimeMillis()}.png"
        var fos: OutputStream? = null
        
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver?.also { resolver ->
                    val contentValues = android.content.ContentValues().apply {
                        put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, filename)
                        put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "image/png")
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_PICTURES + "/Longshots")
                    }
                    val imageUri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                    fos = imageUri?.let { resolver.openOutputStream(it) }
                }
            } else {
                val imagesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES).toString() + "/Longshots"
                val image = java.io.File(imagesDir, filename)
                if (!image.parentFile!!.exists()) image.parentFile!!.mkdirs()
                fos = java.io.FileOutputStream(image)
            }

            fos?.use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                Toast.makeText(this, "Longshot saved to Gallery!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun cleanUpEngine() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanUpEngine()
        mediaProjection?.stop()
        if (::floatingView.isInitialized) {
            windowManager.removeView(floatingView)
        }
    }
}
