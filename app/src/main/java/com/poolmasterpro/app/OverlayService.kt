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

    private var editBtnWindow: Button? = null
    private var cueKnobView: KnobView? = null
    private var aimKnobView: KnobView? = null
    private var cueKnobParams: WindowManager.LayoutParams? = null
    private var aimKnobParams: WindowManager.LayoutParams? = null

    private var isPlayMode = false
    private val knobSize = 120
    private val knobHalf = knobSize / 2

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayTypeFlag(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        )

        rootLayout = FrameLayout(this)
        aimView = PoolAimView(this)
        rootLayout.addView(aimView)

        controlBtn = Button(this).apply {
            text = "PLAY MODE"
            textSize = 11f
            setPadding(24, 10, 24, 10)
            setBackgroundColor(0xDD009955.toInt())
            setTextColor(Color.WHITE)
            setOnClickListener {
                enterPlayMode()
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

    private fun overlayTypeFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun enterPlayMode() {
        if (isPlayMode) return
        isPlayMode = true

        // Table borders & background screen become touch-through
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        controlBtn.visibility = View.GONE
        windowManager.updateViewLayout(rootLayout, params)

        showEditButton()
        showLiveAimKnobs()
        aimView.invalidate()
    }

    private fun enterEditMode() {
        isPlayMode = false
        removeEditButton()
        removeLiveAimKnobs()

        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        controlBtn.visibility = View.VISIBLE
        windowManager.updateViewLayout(rootLayout, params)
        aimView.invalidate()
    }

    private fun showLiveAimKnobs() {
        removeLiveAimKnobs()

        val cParams = WindowManager.LayoutParams(
            knobSize,
            knobSize,
            overlayTypeFlag(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (aimView.cueX - knobHalf).toInt()
            y = (aimView.cueY - knobHalf).toInt()
        }

        val cKnob = KnobView(
            this,
            0xEEFFFFFF.toInt(),
            getStartPos = { PointF(aimView.cueX, aimView.cueY) },
            onMovePoint = { nx, ny ->
                val clampedX = nx.coerceIn(aimView.leftWall, aimView.rightWall)
                val clampedY = ny.coerceIn(aimView.topWall, aimView.bottomWall)
                aimView.cueX = clampedX
                aimView.cueY = clampedY
                aimView.invalidate()
                cParams.x = (clampedX - knobHalf).toInt()
                cParams.y = (clampedY - knobHalf).toInt()
                try {
                    windowManager.updateViewLayout(cueKnobView, cParams)
                } catch (_: Exception) {
                }
            }
        )

        val aParams = WindowManager.LayoutParams(
            knobSize,
            knobSize,
            overlayTypeFlag(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (aimView.aimX - knobHalf).toInt()
            y = (aimView.aimY - knobHalf).toInt()
        }

        val aKnob = KnobView(
            this,
            0xEE00FF55.toInt(),
            getStartPos = { PointF(aimView.aimX, aimView.aimY) },
            onMovePoint = { nx, ny ->
                val clampedX = nx.coerceIn(aimView.leftWall, aimView.rightWall)
                val clampedY = ny.coerceIn(aimView.topWall, aimView.bottomWall)
                aimView.aimX = clampedX
                aimView.aimY = clampedY
                aimView.invalidate()
                aParams.x = (clampedX - knobHalf).toInt()
                aParams.y = (clampedY - knobHalf).toInt()
                try {
                    windowManager.updateViewLayout(aimKnobView, aParams)
                } catch (_: Exception) {
                }
            }
        )

        cueKnobParams = cParams
        aimKnobParams = aParams
        cueKnobView = cKnob
        aimKnobView = aKnob

        windowManager.addView(cKnob, cParams)
        windowManager.addView(aKnob, aParams)
    }

    private fun removeLiveAimKnobs() {
        cueKnobView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        aimKnobView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        cueKnobView = null
        aimKnobView = null
        cueKnobParams = null
        aimKnobParams = null
    }

    private fun showEditButton() {
        if (editBtnWindow != null) return

        val editParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayTypeFlag(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 80
        }

        editBtnWindow = Button(this).apply {
            text = "EDIT BORDERS"
            textSize = 11f
            setPadding(24, 10, 24, 10)
            setBackgroundColor(0xEEDD6600.toInt())
            setTextColor(Color.WHITE)
            setOnClickListener {
                enterEditMode()
            }
        }

        windowManager.addView(editBtnWindow, editParams)
    }

    private fun removeEditButton() {
        editBtnWindow?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        editBtnWindow = null
    }

    override fun onDestroy() {
        super.onDestroy()
        removeEditButton()
        removeLiveAimKnobs()
        if (::rootLayout.isInitialized) {
            try {
                windowManager.removeView(rootLayout)
            } catch (_: Exception) {
            }
        }
    }

    inner class KnobView(
        context: Context,
        private val colorInt: Int,
        private val getStartPos: () -> PointF,
        private val onMovePoint: (Float, Float) -> Unit
    ) : View(context) {

        private var offsetX = 0f
        private var offsetY = 0f

        private val fillPaint = Paint().apply {
            color = colorInt
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private val strokePaint = Paint().apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width * 0.5f
            val cy = height * 0.5f
            val r = minOf(cx, cy) * 0.45f
            canvas.drawCircle(cx, cy, r, fillPaint)
            canvas.drawCircle(cx, cy, r, strokePaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val start = getStartPos()
                    offsetX = event.rawX - start.x
                    offsetY = event.rawY - start.y
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val targetX = event.rawX - offsetX
                    val targetY = event.rawY - offsetY
                    onMovePoint(targetX, targetY)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    inner class PoolAimView(context: Context) : View(context) {

        private var initialized = false

        var leftWall = 100f
        var topWall = 250f
        var rightWall = 600f
        var bottomWall = 900f

        var cueX = 350f
        var cueY = 720f
        var aimX = 350f
        var aimY = 450f

        // 1 = Cue, 2 = Aim, 3 = Top-Left, 4 = Top-Right, 5 = Bottom-Left, 6 = Bottom-Right
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

            if (!isPlayMode) {
                drawHandle(canvas, cueX, cueY, 26f, 0xEEFFFFFF.toInt())
                drawHandle(canvas, aimX, aimY, 26f, 0xEE00FF55.toInt())

                drawHandle(canvas, leftWall, topWall, 24f, 0xEE0088FF.toInt())
                drawHandle(canvas, rightWall, topWall, 24f, 0xEE0088FF.toInt())
                drawHandle(canvas, leftWall, bottomWall, 24f, 0xEE0088FF.toInt())
                drawHandle(canvas, rightWall, bottomWall, 24f, 0xEE0088FF.toInt())
            }
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
            if (isPlayMode) return false

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
