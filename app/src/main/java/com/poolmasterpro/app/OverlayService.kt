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

    private var hubBtnWindow: Button? = null
    private var hubBtnParams: WindowManager.LayoutParams? = null

    private var menuPanelWindow: LinearLayout? = null
    private var menuPanelParams: WindowManager.LayoutParams? = null

    private var modeToggleBtn: Button? = null
    private var btn2Line: Button? = null
    private var btn3Line: Button? = null
    private var btn5Line: Button? = null

    private var cueKnobView: KnobView? = null
    private var aimKnobView: KnobView? = null

    private var isPlayMode = false
    private var isMenuOpen = false
    private var lineMode = 3 // 2 = 2L, 3 = 3L, 5 = 5L

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

        showSonoBhaiButton()
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

    private fun showSonoBhaiButton() {
        removeSonoBhaiButton()
        removeMenuPanel()

        val bParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayTypeFlag(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 75
        }

        val btn = Button(this).apply {
            text = "SonoBhai"
            textSize = 12f
            setTextColor(Color.WHITE)
            setPadding(24, 10, 24, 10)
            background = makePillBg(0xEE111827.toInt())
            setOnClickListener {
                toggleMenuPanel()
            }
        }

        var lastRawX = 0f
        var lastRawY = 0f
        var startRawX = 0f
        var startRawY = 0f
        var dragging = false

        btn.setOnTouchListener { _, event ->
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
                        bParams.x = max(0, bParams.x + dx.toInt())
                        bParams.y = max(0, bParams.y + dy.toInt())
                        try {
                            windowManager.updateViewLayout(btn, bParams)
                        } catch (_: Exception) {
                        }
                        if (isMenuOpen) {
                            syncMenuPosition()
                        }
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

        hubBtnParams = bParams
        hubBtnWindow = btn
        windowManager.addView(btn, bParams)
    }

    private fun toggleMenuPanel() {
        if (isMenuOpen) {
            isMenuOpen = false
            removeMenuPanel()
        } else {
            isMenuOpen = true
            showMenuPanel()
        }
    }

    private fun syncMenuPosition() {
        val bParams = hubBtnParams ?: return
        val mParams = menuPanelParams ?: return
        val panel = menuPanelWindow ?: return

        mParams.x = max(0, bParams.x)
        mParams.y = max(0, bParams.y + 82)
        try {
            windowManager.updateViewLayout(panel, mParams)
        } catch (_: Exception) {
        }
    }

    private fun showMenuPanel() {
        removeMenuPanel()
        val bParams = hubBtnParams ?: return

        val mParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayTypeFlag(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = max(0, bParams.x)
            y = max(0, bParams.y + 82)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(12, 10, 12, 10)
            background = makePillBg(0xDD1B1F24.toInt())
        }

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val toggleBtn = Button(this).apply {
            text = if (isPlayMode) "EDIT BORDERS" else "PLAY MODE"
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(20, 8, 20, 8)
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

        row1.addView(toggleBtn)
        row1.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(10, 1) })
        row1.addView(offBtn)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 0)
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

        val b5 = Button(this).apply {
            text = "5L"
            textSize = 11f
            setTextColor(Color.WHITE)
            setPadding(20, 8, 20, 8)
            setOnClickListener { setLineMode(5) }
        }

        row2.addView(b2)
        row2.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(8, 1) })
        row2.addView(b3)
        row2.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(8, 1) })
        row2.addView(b5)

        container.addView(row1)
        container.addView(row2)

        modeToggleBtn = toggleBtn
        btn2Line = b2
        btn3Line = b3
        btn5Line = b5
        menuPanelParams = mParams
        menuPanelWindow = container

        refreshLineModeButtons()
        windowManager.addView(container, mParams)
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
        btn5Line?.background = makePillBg(if (lineMode == 5) 0xFFDD5500.toInt() else 0x88333A42.toInt())
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

    private fun removeMenuPanel() {
        menuPanelWindow?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        menuPanelWindow = null
        menuPanelParams = null
        modeToggleBtn = null
        btn2Line = null
        btn3Line = null
        btn5Line = null
    }

    private fun removeSonoBhaiButton() {
        hubBtnWindow?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        hubBtnWindow = null
        hubBtnParams = null
    }

    override fun onDestroy() {
        super.onDestroy()
        isMenuOpen = false
        removeMenuPanel()
        removeSonoBhaiButton()
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

        private val line4Paint = Paint().apply {
            color = 0xEEFF55FF.toInt()
            strokeWidth = 4f
            isAntiAlias = true
        }

        private val line5Paint = Paint().apply {
            color = 0xEEFF9900.toInt()
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
