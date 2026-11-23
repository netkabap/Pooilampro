package com.poolmasterpro.app

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null
    private var isLocked = false
    private var currentMode = "Bank Shot"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rootLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 60, 40, 60)
            setBackgroundColor(Color.parseColor("#12151C"))
        }

        val titleText = TextView(this).apply {
            text = "PoolMasterPro Overlay"
            textSize = 22f
            setTextColor(Color.WHITE)
            setPadding(0, 0, 0, 24)
        }

        val statusText = TextView(this).apply {
            text = "Mode: $currentMode | Status: Ready"
            textSize = 15f
            setTextColor(Color.parseColor("#00FFA0"))
            setPadding(0, 0, 0, 32)
        }

        val permBtn = Button(this).apply {
            text = "1. Grant Overlay Permission"
            setOnClickListener { requestOverlayPermission() }
        }

        val showBtn = Button(this).apply {
            text = "2. Show / Update Overlay"
            setOnClickListener {
                if (canDrawOverlay()) {
                    showFloatingOverlay(statusText)
                } else {
                    Toast.makeText(context, "Pehle Overlay Permission on karein", Toast.LENGTH_SHORT).show()
                    requestOverlayPermission()
                }
            }
        }

        val modeBtn = Button(this).apply {
            text = "3. Auto-Select Mode (Bank / Straight / Short)"
            setOnClickListener {
                currentMode = when (currentMode) {
                    "Bank Shot" -> "Straight Shot"
                    "Straight Shot" -> "Precision Short"
                    else -> "Bank Shot"
                }
                statusText.text = "Mode: $currentMode | Overlay: ${if (overlayView != null) "Active" else "Ready"}"
                if (overlayView != null) {
                    removeFloatingOverlay()
                    showFloatingOverlay(statusText)
                }
            }
        }

        val lockBtn = Button(this).apply {
            text = "4. Lock / Unlock Drag"
            setOnClickListener {
                isLocked = !isLocked
                Toast.makeText(
                    context,
                    if (isLocked) "Overlay Locked" else "Overlay Unlocked",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        val hideBtn = Button(this).apply {
            text = "5. Hide Overlay"
            setOnClickListener {
                removeFloatingOverlay()
                statusText.text = "Mode: $currentMode | Overlay: Hidden"
            }
        }

        rootLayout.addView(titleText)
        rootLayout.addView(statusText)
        rootLayout.addView(permBtn)
        rootLayout.addView(showBtn)
        rootLayout.addView(modeBtn)
        rootLayout.addView(lockBtn)
        rootLayout.addView(hideBtn)

        setContentView(rootLayout)
    }

    private fun canDrawOverlay(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)
    }

    private fun requestOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        } else {
            Toast.makeText(this, "Permission pehle se allowed hai", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showFloatingOverlay(statusText: TextView) {
        removeFloatingOverlay()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 120
            y = 260
        }

        val bg = GradientDrawable().apply {
            setColor(Color.parseColor("#CC1B2230"))
            cornerRadius = 28f
            setStroke(4, Color.parseColor("#00FFA0"))
        }

        val badge = TextView(this).apply {
            text = "[$currentMode] Drag Me"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(28, 18, 28, 18)
            background = bg
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        badge.setOnTouchListener { _, event ->
            if (isLocked) return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(badge, params)
                    true
                }
                else -> false
            }
        }

        overlayView = badge
        windowManager?.addView(badge, params)
        statusText.text = "Mode: $currentMode | Overlay: Active"
    }

    private fun removeFloatingOverlay() {
        overlayView?.let {
            try {
                windowManager?.removeView(it)
            } catch (_: Exception) {
            }
        }
        overlayView = null
    }

    override fun onDestroy() {
        removeFloatingOverlay()
        super.onDestroy()
    }
}
