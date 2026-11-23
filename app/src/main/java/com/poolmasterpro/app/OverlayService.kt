package com.poolmasterpro.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import kotlin.math.hypot
import kotlin.math.max

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var rootLayout: FrameLayout
    private lateinit var aimView: PoolAimView
    private lateinit var params: WindowManager.LayoutParams

    private var topBarWindow: LinearLayout? = null
    private var modeToggleBtn: Button? = null

    private var lineBarWindow: LinearLayout? = null
    private var btn2Line: Button? = null
    private var btn3Line: Button? = null
    private var btnUnlimited: Button? = null

    private var cueKnobView: KnobView? = null
    private var aimKnobView: KnobView? = null

    private var isPlayMode = false
    private var lineMode = 3 // 2 = 2 Line, 3 = 3 Line, 99 = Unlimited
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
        windowManager.addView(rootLayout, params)

        showTopControlBar()
        showLineModeBar()
    }

    private fun overlayTypeFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun makePillBg(colorInt: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(colorInt)
            cornerRadius = 18f
        }
    }

    private fun showTopControlBar() {
        removeTopControlBar()

        val barParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayTypeFlag(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 70
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(10, 8, 10, 8)
            background = makePillBg(0xCC1B1F24.toInt())
        }

        val toggleBtn = Button(this).apply {
            text = if (isPlayMode) "EDIT BORDERS" else "PLAY MODE"
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(22, 8, 22, 8)
            background = makePillBg(if (isPlayMode) 0xEEDD6600.toInt() else 0xEE009955.toInt())
            setOnClickListener {
                if (isPlayMode) enterEditMode() else enterPlayMode()
            }
        }

        val offBtn = Button(this).apply {
            text = "OFF"
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(20, 8, 20, 8)
            background = makePillBg(0xEED92626.toInt())
            setOnClickListener {
                stopSelf()
            }
        }

        bar.addView(toggleBtn)
        bar.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(10, 1) })
        bar.addView(offBtn)

        attachDragToView(bar) { dx, dy ->
            barParams.x = max(0, barParams.x + dx.toInt())
            barParams.y = max(0, barParams.y + dy.toInt())
            try {
                windowManager.updateViewLayout(bar, barParams)
            } catch (_: Exception) {
            }
        }

        modeToggleBtn = toggleBtn
        topBarWindow = bar
        windowManager.addView(bar, barParams)
    }

    private fun showLineModeBar() {
        removeLineModeBar()

        val barParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayTypeFlag(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 165
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(10, 8, 10, 8)
            background = makePillBg(0xCC1B1F24.toInt())
        }

        val b2 = Button(this).apply {
            text = "2L"
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(20, 8, 20, 8)
            setOnClickListener { setLineMode(2) }
        }

        val b3 = Button(this).apply {
            text = "3L"
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(20, 8, 20, 8)
            setOnClickListener { setLineMode(3) }
        }

        val bUnlim = Button(this).apply {
            text = "∞"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(22, 8, 22, 8)
            setOnClickListener { setLineMode(99) }
        }

        bar.addView(b2)
        bar.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(8, 1) })
        bar.addView(b3)
        bar.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(8, 1) })
        bar.addView(bUnlim)

        attachDragToView(bar) { dx, dy ->
            barParams.x = max(0, barParams.x + dx.toInt())
            barParams.y = max(0, barParams.y + dy.toInt())
            try {
                windowManager.updateViewLayout(bar, barParams)
            } catch (_: Exception) {
            }
        }

        btn2Line = b2
        btn3Line = b3
        btnUnlimited = bUnlim
        lineBarWindow = bar

        refreshLineModeButtons()
        windowManager.addView(bar, barParams)
    }

    private fun setLineMode(mode: Int) {
        lineMode = mode
        aimView.lineCountSetting = mode
        aimView.invalidate()
        refreshLineModeButtons()
    }

    private fun refreshLineModeButtons() {
        btn2Line?.background = makePillBg(if (lineMode == 2) 0xFF0088FF.toInt() else 0x88333A42.toInt())
        btn3Line?.background = makePillBg(if (lineMode == 3) 0xFF00AA55.toInt() else 0x88333A42.toInt())
        btnUnlimited?.background = makePillBg(if (lineMode == 99) 0xFFDD5500.toInt() else 0x88333A42.toInt())
    }

    private fun attachDragToView(targetView: View, onDelta: (Float, Float) -> Unit) {
        var lastRawX = 0f
        var lastRawY = 0f
        var startRawX = 0f
        var startRawY = 0f
        var dragging = false

        targetView.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    startRawX = event.rawX
                    startRawY = event.rawY
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    dragging = false
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - lastRawX
                    val dy = event.rawY - lastRawY
                    if (!dragging && hypot(event.rawX - startRawX, event.rawY - startRawY) > 14f) {
                        dragging = true
                    }
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    if (dragging) {
                        onDelta(dx, dy)
                        true
                    } else {
                        false
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val wasDragging = dragging
                    dragging = false
                    wasDragging
                }
                else -> false
            }
        }
    }

    private fun enterPlayMode() {
        if (isPlayMode) return
        isPlayMode = true

        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        windowManager.updateViewLayout(rootLayout, params)

        modeToggleBtn?.text = "EDIT BORDERS"
        modeToggleBtn?.background = makePillBg(0xEEDD6600.toInt())

        showLiveAimKnobs()
        aimView.invalidate()
    }

    private fun enterEditMode() {
        isPlayMode = false
        removeLiveAimKnobs()

        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        windowManager.updateViewLayout(rootLayout, params)

        modeToggleBtn?.text = "PLAY MODE"
        modeToggleBtn?.background = makePillBg(0xEE009955.toInt())

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
    }

    private fun removeTopControlBar() {
        topBarWindow?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        topBarWindow = null
        modeToggleBtn = null
    }

    private fun removeLineModeBar() {
        lineBarWindow?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        lineBarWindow = null
        btn2Line = null
        btn3Line = null
        btnUnlimited = null
    }

    override fun onDestroy() {
        super.onDestroy()
        removeTopControlBar()
        removeLineModeBar()
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
                    onMovePoint(event.rawX - offsetX, event.rawY - offsetY)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    inner class PoolAimView(context: Context) : View(context) {

        private var initialized = false
        var lineCountSetting = 3

        var leftWall = 100f
        var topWall = 250f
        var rightWall = 600f
        var bottomWall = 900f

        var cueX = 350f
        var cueY = 720f
        var aimX = 350f
        var aimY = 450f

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

        private val extraBouncePaint = Paint().apply {
            color = 0xEEFF55FF.toInt()
            strokeWidth = 4f
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

            if (!isPlayMode) {
                canvas.drawRect(leftWall, topWall, rightWall, bottomWall, tablePaint)

                val midX = (leftWall + rightWall) * 0.5f
                val pockets = listOf(
                    PointF(leftWall, topWall), PointF(midX, topWall), PointF(rightWall, topWall),
                    PointF(leftWall, bottomWall), PointF(midX, bottomWall), PointF(rightWall, bottomWall)
                )
                for (p in pockets) {
                    canvas.drawCircle(p.x, p.y, 18f, pocketPaint)
                }
            }

            canvas.drawLine(cueX, cueY, aimX, aimY, line1Paint)

            val bounceCount = when (lineCountSetting) {
                2 -> 1
                3 -> 2
                else -> 30
            }
            drawReflectionsFromAim(canvas, aimX, aimY, aimX - cueX, aimY - cueY, bounceCount)

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

  
