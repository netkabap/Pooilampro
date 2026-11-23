package com.poolmasterpro.app

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(50, 50, 50, 50)
            setBackgroundColor(0xFF121212.toInt())
        }

        val title = TextView(this).apply {
            text = "PoolMaster 3-Line Tool"
            textSize = 24f
            setTextColor(0xFF00FF88.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }

        val btnPerm = Button(this).apply {
            text = "1. GRANT OVERLAY PERMISSION"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                    startActivity(intent)
                } else {
                    Toast.makeText(this@MainActivity, "Permission Already Granted!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val btnStart = Button(this).apply {
            text = "2. START 3-LINE OVERLAY"
            setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "Pehle Permission Grant Karein!", Toast.LENGTH_SHORT).show()
                } else {
                    startService(Intent(this@MainActivity, OverlayService::class.java))
                    moveTaskToBack(true)
                }
            }
        }

        val btnStop = Button(this).apply {
            text = "3. STOP OVERLAY"
            setOnClickListener {
                stopService(Intent(this@MainActivity, OverlayService::class.java))
            }
        }

        layout.addView(title)
        layout.addView(btnPerm)
        layout.addView(btnStart)
        layout.addView(btnStop)

        setContentView(layout)
    }
}
