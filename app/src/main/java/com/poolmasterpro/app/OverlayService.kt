package com.poolmasterpro.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import kotlin.math.hypot

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var rootLayout: FrameLayout
    private lateinit var aimView: PoolAimView
    private lateinit var controlBtn: Button
    private lateinit var params: WindowManager.LayoutParams
    private var isLocked = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        rootLayout = FrameLayout(this)
        aimView = PoolAimView(this)
        rootLayout.addView(aimView)

        controlBtn = Button(this).apply {
            text = "LOCK AIM (PLAY)"
            textSize = 12f
            setBackgroundColor(0xCC00AA55.toInt())
            setTextColor(Color.WHITE)
            setOnClickListener {
                toggleLockMode()
            }
        }

        val btnParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            topMargin = 20
        }

        rootLayout.addView(controlBtn, btnParams)
        windowManager.addView(rootLayout, params)
    }

    private fun toggleLockMode() {
        isLocked = !isLocked
        if (isLocked) {
            params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
            controlBtn.text = "UNLOCK AIM (EDIT)"
            controlBtn.setBackgroundColor(0xCCFF3333.toInt())
            showFloatingUnlockButton()
        }
        windowManager.updateViewLayout(rootLayout, params)
    }

    private var unlockBtnWindow: Button? = null

    private fun showFloatingUnlockButton() {
        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val unlockParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 30
            y = 20
        }

        unlockBtnWindow = Button(this).apply {
            text = "EDIT AIM"
            textSize = 12f
            setBackgroundColor(0xEEFF8800.toInt())
            setTextColor(Color.WHITE)
            setOnClickListener {
                isLocked = false
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                windowManager.updateViewLayout(rootLayout, params)
                windowManager.removeView(unlockBtnWindow)
                unlockBtnWindow = null
            }
        }
        windowManager.addView(unlockBtnWindow, unlockParams)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::rootLayout.isInitialized) windowManager.removeView(rootLayout)
        if (unlockBtnWindow != null) windowManager.removeView(unlockBtnWindow)
    }

    inner class PoolAimView(context: Context) : View(context) {

        private var leftWall = 250f
        private var topWall = 180f
        private var rightWall = 1800f
        private var bottomWall = 920f

        private var cueX = 700f
        private var cueY = 550f
        private var aimX = 1100f
        private var aimY = 400f

        private var activeHandle = 0

        private val tablePaint = Paint().apply {
            color = 0x8800FFCC.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        private val line1Paint = Paint().apply {
            color = Color.WHITE
            strokeWidth = 5f
            isAntiAlias = true
        }

        private val line2Paint = Paint().apply {
            color = Color.YELLOW
            strokeWidth = 5f
            isAntiAlias = true
        }

        private val line3Paint = Paint().apply {
            color = Color.CYAN
            strokeWidth = 5f
            isAntiAlias = true
        }

        private val handlePaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            canvas.drawRect(leftWall, topWall, rightWall, bottomWall, tablePaint)

            val midX = (leftWall + rightWall) / 2f
            val pockets = listOf(
                PointF(leftWall, topWall), PointF(midX, topWall), PointF(rightWall, topWall),
                PointF(leftWall, bottomWall), PointF(midX, bottomWall), PointF(rightWall, bottomWall)
            )
            handlePaint.color = 0xAAFF0055.toInt()
            for (p in pockets) {
                canvas.drawCircle(p.x, p.y, 22f, handlePaint)
            }

            drawBouncingLines(canvas, cueX, cueY, aimX - cueX, aimY - cueY, 3)

            handlePaint.color = 0xCCFFFFFF.toInt()
            canvas.drawCircle(cueX, cueY, 32f, handlePaint)

            handlePaint.color = 0xCC00FF44.toInt()
            canvas.drawCircle(aimX, aimY, 32f, handlePaint)

            handlePaint.color = 0xCC0088FF.toInt()
            canvas.drawCircle(leftWall, topWall, 28f, handlePaint)
            canvas.drawCircle(rightWall, bottomWall, 28f, handlePaint)
        }

        private fun drawBouncingLines(canvas: Canvas, startX: Float, startY: Float, dirX: Float, dirY: Float, bounces: Int) {
            var x = startX
            var y = startY
            var vx = dirX
            var vy = dirY

            val len = hypot(vx, vy)
            if (len == 0f) return
            vx /= len
            vy /= len

            val paints = listOf(line1Paint, line2Paint, line3Paint)

            for (i in 0 until bounces) {
                var tMin = Float.MAX_VALUE
                var hitVertical = false

                if (vx > 0) {
                    val t = (rightWall - x) / vx
                    if (t in 0.1f..tMin) { tMin = t; hitVertical = true }
                } else if (vx < 0) {
                    val t = (leftWall - x) / vx
                    if (t in 0.1f..tMin) { tMin = t; hitVertical = true }
                }

                if (vy > 0) {
                    val t = (bottomWall - y) / vy
                    if (t in 0.1f..tMin) { tMin = t; hitVertical = false }
                } else if (vy < 0) {
                    val t = (topWall - y) / vy
                    if (t in 0.1f..tMin) { tMin = t; hitVertical = false }
                }

                if (tMin == Float.MAX_VALUE) break

                val nextX = x + vx * tMin
                val nextY = y + vy * tMin

                canvas.drawLine(x, y, nextX, nextY, paints[i % paints.size])

                x = nextX
                y = nextY
                if (hitVertical) vx = -vx else vy = -vy
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (isLocked) return false
            val tx = event.x
            val ty = event.y

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    activeHandle = when {
                        hypot(tx - cueX, ty - cueY) < 70f -> 1
                        hypot(tx - aimX, ty - aimY) < 70f -> 2
                        hypot(tx - leftWall, ty - topWall) < 70f -> 3
                        hypot(tx - rightWall, ty - bottomWall) < 70f -> 4
                        else -> 0
                    }
                    return activeHandle != 0
                }
                MotionEvent.ACTION_MOVE -> {
                    when (activeHandle) {
                        1 -> { cueX = tx; cueY = ty }
                        2 -> { aimX = tx; aimY = ty }
                        3 -> { leftWall = tx; topWall = ty }
                        4 -> { rightWall = tx; bottomWall = ty }
                    }
                    invalidate()
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    activeHandle = 0
                }
            }
            return super.onTouchEvent(event)
        }
    }
}
