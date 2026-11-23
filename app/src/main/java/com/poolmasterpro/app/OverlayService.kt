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
import kotlin.math.max

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var rootLayout: FrameLayout
    private lateinit var aimView: PoolAimView
    private lateinit var controlBtn: Button
    private lateinit var params: WindowManager.LayoutParams
    private var unlockBtnWindow: Button? = null
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
            text = "LOCK AIM"
            textSize = 11f
            setPadding(24, 10, 24, 10)
            setBackgroundColor(0xDD009955.toInt())
            setTextColor(Color.WHITE)
            setOnClickListener {
                lockOverlay()
            }
        }

        val btnParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            leftMargin = 24
            topMargin = 80
        }

        rootLayout.addView(controlBtn, btnParams)
        windowManager.addView(rootLayout, params)
    }

    private fun lockOverlay() {
        if (isLocked) return
        isLocked = true

        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        rootLayout.visibility = View.VISIBLE
        controlBtn.visibility = View.GONE
        windowManager.updateViewLayout(rootLayout, params)

        showFloatingUnlockButton()
    }

    private fun unlockOverlay() {
        isLocked = false
        removeUnlockButton()

        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        controlBtn.visibility = View.VISIBLE
        windowManager.updateViewLayout(rootLayout, params)
        aimView.invalidate()
    }

    private fun showFloatingUnlockButton() {
        if (unlockBtnWindow != null) return

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
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 80
        }

        unlockBtnWindow = Button(this).apply {
            text = "EDIT AIM"
            textSize = 11f
            setPadding(24, 10, 24, 10)
            setBackgroundColor(0xEEDD6600.toInt())
            setTextColor(Color.WHITE)
            setOnClickListener {
                unlockOverlay()
            }
        }

        windowManager.addView(unlockBtnWindow, unlockParams)
    }

    private fun removeUnlockButton() {
        unlockBtnWindow?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        unlockBtnWindow = null
    }

    override fun onDestroy() {
        super.onDestroy()
        removeUnlockButton()
        if (::rootLayout.isInitialized) {
            try {
                windowManager.removeView(rootLayout)
            } catch (_: Exception) {
            }
        }
    }

    inner class PoolAimView(context: Context) : View(context) {

        private var initialized = false

        private var leftWall = 100f
        private var topWall = 250f
        private var rightWall = 600f
        private var bottomWall = 900f

        private var cueX = 350f
        private var cueY = 720f
        private var aimX = 350f
        private var aimY = 450f

        private var activeHandle = 0

        private val tablePaint = Paint().apply {
            color = 0xAA00FFCC.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 5f
            isAntiAlias = true
        }

        private val line1Paint = Paint().apply {
            color = Color.WHITE
            strokeWidth = 6f
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

        private val pocketPaint = Paint().apply {
            color = 0xAAFF0055.toInt()
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private val handlePaint = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private val handleBorderPaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (w <= 0 || h <= 0) return

            if (!initialized) {
                val padX = w * 0.14f
                val padY = h * 0.22f
                leftWall = padX
                rightWall = w - padX
                topWall = padY
                bottomWall = h - padY

                cueX = (leftWall + rightWall) * 0.5f
                cueY = topWall + (bottomWall - topWall) * 0.72f
                aimX = (leftWall + rightWall) * 0.5f
                aimY = topWall + (bottomWall - topWall) * 0.35f
                initialized = true
            } else {
                clampToScreen(w.toFloat(), h.toFloat())
            }
        }

        private fun clampToScreen(w: Float, h: Float) {
            val margin = 40f
            leftWall = leftWall.coerceIn(margin, w - margin - 120f)
            rightWall = rightWall.coerceIn(leftWall + 120f, w - margin)
            topWall = topWall.coerceIn(margin + 90f, h - margin - 120f)
            bottomWall = bottomWall.coerceIn(topWall + 120f, h - margin)

            cueX = cueX.coerceIn(leftWall, rightWall)
            cueY = cueY.coerceIn(topWall, bottomWall)
            aimX = aimX.coerceIn(leftWall, rightWall)
            aimY = aimY.coerceIn(topWall, bottomWall)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            canvas.drawRect(leftWall, topWall, rightWall, bottomWall, tablePaint)

            val midX = (leftWall + rightWall) * 0.5f
            val pockets = listOf(
                PointF(leftWall, topWall), PointF(midX, topWall), PointF(rightWall, topWall),
                PointF(leftWall, bottomWall), PointF(midX, bottomWall), PointF(rightWall, bottomWall)
            )
            for (p in pockets) {
                canvas.drawCircle(p.x, p.y, 18f, pocketPaint)
            }

            canvas.drawLine(cueX, cueY, aimX, aimY, line1Paint)
            drawReflectionsFromAim(canvas, aimX, aimY, aimX - cueX, aimY - cueY, 2)

            drawHandle(canvas, cueX, cueY, 26f, 0xEEFFFFFF.toInt())
            drawHandle(canvas, aimX, aimY, 26f, 0xEE00FF55.toInt())

            drawHandle(canvas, leftWall, topWall, 24f, 0xEE0088FF.toInt())
            drawHandle(canvas, rightWall, topWall, 24f, 0xEE0088FF.toInt())
            drawHandle(canvas, leftWall, bottomWall, 24f, 0xEE0088FF.toInt())
            drawHandle(canvas, rightWall, bottomWall, 24f, 0xEE0088FF.toInt())
        }

        private fun drawHandle(canvas: Canvas, x: Float, y: Float, radius: Float, fillColor: Int) {
            handlePaint.color = fillColor
            canvas.drawCircle(x, y, radius, handlePaint)
            canvas.drawCircle(x, y, radius, handleBorderPaint)
        }

        private fun drawReflectionsFromAim(
            canvas: Canvas,
            startX: Float,
            startY: Float,
            dirX: Float,
            dirY: Float,
            bounces: Int
        ) {
            var x = startX.coerceIn(leftWall, rightWall)
            var y = startY.coerceIn(topWall, bottomWall)
            var vx = dirX
            var vy = dirY

            val len = hypot(vx, vy)
            if (len < 0.001f) return
            vx /= len
            vy /= len

            val bouncePaints = listOf(line2Paint, line3Paint)

            for (i in 0 until bounces) {
                var tMin = Float.MAX_VALUE
                var hitVertical = false

                if (vx > 0.0001f) {
                    val t = (rightWall - x) / vx
                    if (t > 0.5f && t < tMin) {
                        tMin = t
                        hitVertical = true
                    }
                } else if (vx < -0.0001f) {
                    val t = (leftWall - x) / vx
                    if (t > 0.5f && t < tMin) {
                        tMin = t
                        hitVertical = true
                    }
                }

                if (vy > 0.0001f) {
                    val t = (bottomWall - y) / vy
                    if (t > 0.5f && t < tMin) {
                        tMin = t
                        hitVertical = false
                    }
                } else if (vy < -0.0001f) {
                    val t = (topWall - y) / vy
                    if (t > 0.5f && t < tMin) {
                        tMin = t
                        hitVertical = false
                    }
                }

                if (tMin == Float.MAX_VALUE) break

                val nextX = (x + vx * tMin).coerceIn(leftWall, rightWall)
                val nextY = (y + vy * tMin).coerceIn(topWall, bottomWall)

                canvas.drawLine(x, y, nextX, nextY, bouncePaints[i % bouncePaints.size])

                x = nextX
                y = nextY
                if (hitVertical) vx = -vx else vy = -vy
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (isLocked) return false

            val tx = event.x
            val ty = event.y
            val touchRadius = 75f

            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    activeHandle = when {
                        hypot(tx - cueX, ty - cueY) < touchRadius -> 1
                        hypot(tx - aimX, ty - aimY) < touchRadius -> 2
                        hypot(tx - leftWall, ty - topWall) < touchRadius -> 3
                        hypot(tx - rightWall, ty - topWall) < touchRadius -> 4
                        hypot(tx - leftWall, ty - bottomWall) < touchRadius -> 5
                        hypot(tx - rightWall, ty - bottomWall) < touchRadius -> 6
                        else -> 0
                    }
                    return activeHandle != 0
                }

                MotionEvent.ACTION_MOVE -> {
                    val w = width.toFloat()
                    val h = height.toFloat()
                    val minBoxSize = 140f

                    when (activeHandle) {
                        1 -> {
                            cueX = tx.coerceIn(leftWall, rightWall)
                            cueY = ty.coerceIn(topWall, bottomWall)
                        }
                        2 -> {
                            aimX = tx.coerceIn(leftWall, rightWall)
                            aimY = ty.coerceIn(topWall, bottomWall)
                        }
                        3 -> {
                            leftWall = tx.coerceIn(20f, rightWall - minBoxSize)
                            topWall = ty.coerceIn(110f, bottomWall - minBoxSize)
                        }
                        4 -> {
                            rightWall = tx.coerceIn(leftWall + minBoxSize, max(leftWall + minBoxSize, w - 20f))
                            topWall = ty.coerceIn(110f, bottomWall - minBoxSize)
                        }
                        5 -> {
                            leftWall = tx.coerceIn(20f, rightWall - minBoxSize)
                            bottomWall = ty.coerceIn(topWall + minBoxSize, max(topWall + minBoxSize, h - 20f))
                        }
                        6 -> {
                            rightWall = tx.coerceIn(leftWall + minBoxSize, max(leftWall + minBoxSize, w - 20f))
                            bottomWall = ty.coerceIn(topWall + minBoxSize, max(topWall + minBoxSize, h - 20f))
                        }
                    }

                    cueX = cueX.coerceIn(leftWall, rightWall)
                    cueY = cueY.coerceIn(topWall, bottomWall)
                    aimX = aimX.coerceIn(leftWall, rightWall)
                    aimY = aimY.coerceIn(topWall, bottomWall)

                    invalidate()
                    return true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    activeHandle = 0
                }
            }
            return super.onTouchEvent(event)
        }
    }
}
