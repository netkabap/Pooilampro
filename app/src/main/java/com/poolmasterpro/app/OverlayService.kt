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
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var rootLayout: FrameLayout
    private lateinit var aimView: PoolAimView
    private lateinit var params: WindowManager.LayoutParams

    private var hubBtnWindow: Button? = null
    private var hubBtnParams: WindowManager.LayoutParams? = null

    private var menuPanelWindow: LinearLayout? = null
    private var menuPanelParams: WindowManager.LayoutParams? = null

    private var pocketBarWindow: LinearLayout? = null
    private var pocketBarParams: WindowManager.LayoutParams? = null

    private var ringSizeBarWindow: LinearLayout? = null
    private var ringSizeBarParams: WindowManager.LayoutParams? = null

    private var modeToggleBtn: Button? = null
    private var btn3Line: Button? = null
    private var btn5Line: Button? = null
    private val pocketButtons = mutableMapOf<Int, Button>()

    private var cueKnobView: KnobView? = null
    private var aimKnobView: KnobView? = null
    private var aimDimHandleView: DimOffsetHandleView? = null

    private var isPlayMode = false
    private var isMenuOpen = false
    private var lineMode = 3
    private var selectedPocket: Int? = null

    private var greenRingRadius = 16f
    private val dimHandleOffsetX = 68f
    private val dimHandleOffsetY = -58f

    private val knobSize = 110
    private val knobHalf = knobSize / 2
    private val fingerLiftY = 72f

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
        aimView = PoolAimView(this).apply {
            aimRingRadius = greenRingRadius
        }
        rootLayout.addView(aimView)
        windowManager.addView(rootLayout, params)

        showSonoBhaiButton()
        showSeparatePocketBar()
        showRingSizeBar()
    }

    private fun overlayTypeFlag(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    private fun makePillBg(colorInt: Int, radius: Float = 10f): GradientDrawable {
        return GradientDrawable().apply {
            setColor(colorInt)
            cornerRadius = radius
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
            x = 18
            y = 14
        }

        val btn = Button(this).apply {
            text = "SonoBhai"
            textSize = 9f
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setTextColor(Color.WHITE)
            setPadding(12, 4, 12, 4)
            background = makePillBg(0xEE111827.toInt(), 8f)
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

    private fun showSeparatePocketBar() {
        removeSeparatePocketBar()

        val pParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayTypeFlag(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            x = 0
            y = 6
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(6, 3, 6, 3)
            background = makePillBg(0xCC14181E.toInt(), 8f)
        }

        pocketButtons.clear()
        for (p in 1..6) {
            val pBtn = Button(this).apply {
                text = "P$p"
                textSize = 8f
                minWidth = 0
                minimumWidth = 0
                minHeight = 0
                minimumHeight = 0
                setTextColor(Color.WHITE)
                setPadding(8, 3, 8, 3)
                setOnClickListener { togglePocketAimAssist(p) }
            }
            pocketButtons[p] = pBtn
            bar.addView(pBtn)
            if (p < 6) {
                bar.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(3, 1) })
            }
        }

        var lastRawX = 0f
        var lastRawY = 0f
        var startRawX = 0f
        var startRawY = 0f
        var dragging = false

        bar.setOnTouchListener { _, event ->
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
                        pParams.gravity = Gravity.TOP or Gravity.START
                        pParams.x = max(0, pParams.x + dx.toInt())
                        pParams.y = max(0, pParams.y + dy.toInt())
                        try {
                            windowManager.updateViewLayout(bar, pParams)
                        } catch (_: Exception) {
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

        pocketBarParams = pParams
        pocketBarWindow = bar
        refreshPocketButtons()
        windowManager.addView(bar, pParams)
    }
        private fun showRingSizeBar() {
        removeRingSizeBar()

        val rParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayTypeFlag(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.RIGHT
            x = 18
            y = 14
        }

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(5, 3, 5, 3)
            background = makePillBg(0xCC14181E.toInt(), 8f)
        }

        val minusBtn = Button(this).apply {
            text = "G-"
            textSize = 8f
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setTextColor(Color.WHITE)
            setPadding(8, 3, 8, 3)
            background = makePillBg(0x882E7D32.toInt(), 5f)
            setOnClickListener { adjustGreenRingSize(-2.5f) }
        }

        val plusBtn = Button(this).apply {
            text = "G+"
            textSize = 8f
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setTextColor(Color.WHITE)
            setPadding(8, 3, 8, 3)
            background = makePillBg(0x882E7D32.toInt(), 5f)
            setOnClickListener { adjustGreenRingSize(2.5f) }
        }

        bar.addView(minusBtn)
        bar.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(4, 1) })
        bar.addView(plusBtn)

        ringSizeBarParams = rParams
        ringSizeBarWindow = bar
        windowManager.addView(bar, rParams)
    }

    private fun adjustGreenRingSize(delta: Float) {
        greenRingRadius = (greenRingRadius + delta).coerceIn(7f, 36f)
        aimView.aimRingRadius = greenRingRadius
        aimKnobView?.customRadius = greenRingRadius
        aimKnobView?.invalidate()
        aimView.invalidate()
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
        mParams.y = max(0, bParams.y + 44)
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
            y = max(0, bParams.y + 44)
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(6, 5, 6, 5)
            background = makePillBg(0xDD1B1F24.toInt(), 8f)
        }

        val row1 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        val toggleBtn = Button(this).apply {
            text = if (isPlayMode) "EDIT" else "PLAY"
            textSize = 8.5f
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setTextColor(Color.WHITE)
            setPadding(10, 4, 10, 4)
            background = makePillBg(if (isPlayMode) 0xEEDD6600.toInt() else 0xEE009955.toInt(), 6f)
            setOnClickListener {
                if (isPlayMode) enterEditMode() else enterPlayMode()
            }
        }

        val offBtn = Button(this).apply {
            text = "OFF"
            textSize = 8.5f
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setTextColor(Color.WHITE)
            setPadding(10, 4, 10, 4)
            background = makePillBg(0xEED92626.toInt(), 6f)
            setOnClickListener {
                stopSelf()
            }
        }

        row1.addView(toggleBtn)
        row1.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(4, 1) })
        row1.addView(offBtn)

        val row2 = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 4, 0, 0)
        }

        val b3 = Button(this).apply {
            text = "3L"
            textSize = 8.5f
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setTextColor(Color.WHITE)
            setPadding(12, 4, 12, 4)
            setOnClickListener { setLineMode(3) }
        }

        val b5 = Button(this).apply {
            text = "5L"
            textSize = 8.5f
            minWidth = 0
            minimumWidth = 0
            minHeight = 0
            minimumHeight = 0
            setTextColor(Color.WHITE)
            setPadding(12, 4, 12, 4)
            setOnClickListener { setLineMode(5) }
        }

        row2.addView(b3)
        row2.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(4, 1) })
        row2.addView(b5)

        container.addView(row1)
        container.addView(row2)

        modeToggleBtn = toggleBtn
        btn3Line = b3
        btn5Line = b5
        menuPanelParams = mParams
        menuPanelWindow = container

        refreshLineModeButtons()
        windowManager.addView(container, mParams)
    }

    private fun togglePocketAimAssist(pocketNum: Int) {
        selectedPocket = if (selectedPocket == pocketNum) null else pocketNum
        aimView.assistPocket = selectedPocket
        aimView.applyAimAssistFromPocket()
        refreshPocketButtons()
        syncKnobPositions()
        aimView.invalidate()
    }

    private fun refreshPocketButtons() {
        for ((p, btn) in pocketButtons) {
            btn.background = makePillBg(
                if (selectedPocket == p) 0xFF00AAFF.toInt() else 0x88333A42.toInt(),
                5f
            )
        }
    }

    private fun setLineMode(mode: Int) {
        lineMode = mode
        aimView.lineCountSetting = mode
        aimView.invalidate()
        refreshLineModeButtons()
    }

    private fun refreshLineModeButtons() {
        btn3Line?.background = makePillBg(if (lineMode == 3) 0xFF00AA55.toInt() else 0x88333A42.toInt(), 6f)
        btn5Line?.background = makePillBg(if (lineMode == 5) 0xFFDD5500.toInt() else 0x88333A42.toInt(), 6f)
    }

    private fun enterPlayMode() {
        if (isPlayMode) return
        aimView.preserveBallsExact()
        isPlayMode = true

        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        windowManager.updateViewLayout(rootLayout, params)

        modeToggleBtn?.text = "EDIT"
        modeToggleBtn?.background = makePillBg(0xEEDD6600.toInt(), 6f)

        showLiveAimKnobs()
        syncKnobPositions()
        aimView.invalidate()
    }

    private fun enterEditMode() {
        aimView.preserveBallsExact()
        isPlayMode = false
        removeLiveAimKnobs()

        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
        windowManager.updateViewLayout(rootLayout, params)

        modeToggleBtn?.text = "PLAY"
        modeToggleBtn?.background = makePillBg(0xEE009955.toInt(), 6f)

        aimView.invalidate()
    }

    private fun moveAimBallTo(nx: Float, ny: Float) {
        val clampedX = nx.coerceIn(aimView.leftWall, aimView.rightWall)
        val clampedY = ny.coerceIn(aimView.topWall, aimView.bottomWall)
        aimView.aimX = clampedX
        aimView.aimY = clampedY
        aimView.applyAimAssistFromPocket()
        aimView.invalidate()
        syncKnobPositions()
    }

    private fun syncKnobPositions() {
        cueKnobView?.let { cView ->
            val lp = cView.layoutParams as? WindowManager.LayoutParams
            if (lp != null) {
                lp.x = (aimView.cueX - knobHalf).toInt()
                lp.y = (aimView.cueY - knobHalf).toInt()
                try {
                    windowManager.updateViewLayout(cView, lp)
                } catch (_: Exception) {
                }
            }
        }
        aimKnobView?.let { aView ->
            val lp = aView.layoutParams as? WindowManager.LayoutParams
            if (lp != null) {
                lp.x = (aimView.aimX - knobHalf).toInt()
                lp.y = (aimView.aimY - knobHalf).toInt()
                try {
                    windowManager.updateViewLayout(aView, lp)
                } catch (_: Exception) {
                }
            }
        }
        aimDimHandleView?.let { dView ->
            val lp = dView.layoutParams as? WindowManager.LayoutParams
            if (lp != null) {
                lp.x = (aimView.aimX + dimHandleOffsetX - knobHalf).toInt()
                lp.y = (aimView.aimY + dimHandleOffsetY - knobHalf).toInt()
                try {
                    windowManager.updateViewLayout(dView, lp)
                } catch (_: Exception) {
                }
            }
        }
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
            Color.WHITE,
            liftOffsetY = fingerLiftY,
            initialRadius = 15f,
            getStartPos = { PointF(aimView.cueX, aimView.cueY) },
            onMovePoint = { nx, ny ->
                val clampedX = nx.coerceIn(aimView.leftWall, aimView.rightWall)
                val clampedY = ny.coerceIn(aimView.topWall, aimView.bottomWall)
                aimView.cueX = clampedX
                aimView.cueY = clampedY
                aimView.applyAimAssistFromPocket()
                aimView.invalidate()
                syncKnobPositions()
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
            0xFF00FF55.toInt(),
            liftOffsetY = fingerLiftY,
            initialRadius = greenRingRadius,
            getStartPos = { PointF(aimView.aimX, aimView.aimY) },
            onMovePoint = { nx, ny ->
                moveAimBallTo(nx, ny)
            }
        )

        val dParams = WindowManager.LayoutParams(
            knobSize,
            knobSize,
            overlayTypeFlag(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (aimView.aimX + dimHandleOffsetX - knobHalf).toInt()
            y = (aimView.aimY + dimHandleOffsetY - knobHalf).toInt()
        }

        val dHandle = DimOffsetHandleView(
            this,
            offsetXFromBall = dimHandleOffsetX,
            offsetYFromBall = dimHandleOffsetY,
            onDragBallTo = { bx, by ->
                moveAimBallTo(bx, by)
            }
        )

        cueKnobView = cKnob
        aimKnobView = aKnob
        aimDimHandleView = dHandle

        windowManager.addView(cKnob, cParams)
        windowManager.addView(aKnob, aParams)
        windowManager.addView(dHandle, dParams)
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
        aimDimHandleView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        cueKnobView = null
        aimKnobView = null
        aimDimHandleView = null
    }

    private fun removeRingSizeBar() {
        ringSizeBarWindow?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        ringSizeBarWindow = null
        ringSizeBarParams = null
    }

    private fun removeSeparatePocketBar() {
        pocketBarWindow?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        pocketBarWindow = null
        pocketBarParams = null
        pocketButtons.clear()
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
        removeRingSizeBar()
        removeSeparatePocketBar()
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
        private val ringColor: Int,
        private val liftOffsetY: Float,
        initialRadius: Float,
        private val getStartPos: () -> PointF,
        private val onMovePoint: (Float, Float) -> Unit
    ) : View(context) {

        var customRadius: Float = initialRadius
        private var offsetX = 0f
        private var offsetY = 0f

        private val ringPaint = Paint().apply {
            color = ringColor
            style = Paint.Style.STROKE
            strokeWidth = 3.5f
            isAntiAlias = true
        }

        private val centerDotPaint = Paint().apply {
            color = ringColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width * 0.5f
            val cy = height * 0.5f
            val r = customRadius.coerceIn(6f, minOf(cx, cy) - 4f)
            canvas.drawCircle(cx, cy, r, ringPaint)
            canvas.drawCircle(cx, cy, 2f, centerDotPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val start = getStartPos()
                    offsetX = event.rawX - start.x
                    offsetY = (event.rawY - liftOffsetY) - start.y
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    onMovePoint(event.rawX - offsetX, (event.rawY - liftOffsetY) - offsetY)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }

    inner class DimOffsetHandleView(
        context: Context,
        private val offsetXFromBall: Float,
        private val offsetYFromBall: Float,
        private val onDragBallTo: (Float, Float) -> Unit
    ) : View(context) {

        private var grabDeltaX = 0f
        private var grabDeltaY = 0f

        private val dimRingPaint = Paint().apply {
            color = 0x6655E888.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

        private val dimFillPaint = Paint().apply {
            color = 0x2255E888.toInt()
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width * 0.5f
            val cy = height * 0.5f
            canvas.drawCircle(cx, cy, 15f, dimFillPaint)
            canvas.drawCircle(cx, cy, 15f, dimRingPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    val currentHandleX = aimView.aimX + offsetXFromBall
                    val currentHandleY = aimView.aimY + offsetYFromBall
                    grabDeltaX = event.rawX - currentHandleX
                    grabDeltaY = event.rawY - currentHandleY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val newHandleX = event.rawX - grabDeltaX
                    val newHandleY = event.rawY - grabDeltaY
                    onDragBallTo(newHandleX - offsetXFromBall, newHandleY - offsetYFromBall)
                    return true
                }
            }
            return super.onTouchEvent(event)
        }
    }
        inner class PoolAimView(context: Context) : View(context) {

        private var initialized = false
        var lineCountSetting = 3
        var assistPocket: Int? = null
        var aimRingRadius = 16f

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

        private val assistLinePaint = Paint().apply {
            color = 0xEE00FF88.toInt()
            strokeWidth = 4f
            isAntiAlias = true
        }

        private val pocketPaint = Paint().apply {
            color = 0xAAFF0055.toInt()
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        private val handlePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            isAntiAlias = true
        }

        fun preserveBallsExact() {
            cueX = cueX.coerceIn(leftWall, rightWall)
            cueY = cueY.coerceIn(topWall, bottomWall)
            aimX = aimX.coerceIn(leftWall, rightWall)
            aimY = aimY.coerceIn(topWall, bottomWall)
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

            preserveBallsExact()
        }

        private fun getPocketPoints(): List<PointF> {
            val midX = (leftWall + rightWall) * 0.5f
            return listOf(
                PointF(leftWall, topWall),
                PointF(midX, topWall),
                PointF(rightWall, topWall),
                PointF(leftWall, bottomWall),
                PointF(midX, bottomWall),
                PointF(rightWall, bottomWall)
            )
        }

        fun applyAimAssistFromPocket() {
            val pIndex = (assistPocket ?: return) - 1
            val pockets = getPocketPoints()
            if (pIndex !in pockets.indices) return
            val targetPocket = pockets[pIndex]

            val dx = targetPocket.x - aimX
            val dy = targetPocket.y - aimY
            val dist = hypot(dx, dy)
            if (dist < 1f) return

            val ballRadius = max(10f, aimRingRadius)
            val ux = dx / dist
            val uy = dy / dist

            val contactX = (aimX - ux * ballRadius).coerceIn(leftWall, rightWall)
            val contactY = (aimY - uy * ballRadius).coerceIn(topWall, bottomWall)

            val cueDx = contactX - cueX
            val cueDy = contactY - cueY
            val cueLen = hypot(cueDx, cueDy)
            if (cueLen > 1f) {
                val angleToContact = atan2(cueDy, cueDx)
                val currentAimDist = max(40f, hypot(aimX - cueX, aimY - cueY))
                aimX = (cueX + cos(angleToContact) * currentAimDist).coerceIn(leftWall, rightWall)
                aimY = (cueY + sin(angleToContact) * currentAimDist).coerceIn(topWall, bottomWall)
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val pockets = getPocketPoints()

            if (!isPlayMode) {
                canvas.drawRect(leftWall, topWall, rightWall, bottomWall, tablePaint)
                for (p in pockets) {
                    canvas.drawCircle(p.x, p.y, 16f, pocketPaint)
                }
            }

            assistPocket?.let { pNum ->
                val p = pockets.getOrNull(pNum - 1)
                if (p != null) {
                    canvas.drawLine(aimX, aimY, p.x, p.y, assistLinePaint)
                }
            }

            canvas.drawLine(cueX, cueY, aimX, aimY, line1Paint)

            val bounceCount = when (lineCountSetting) {
                5 -> 4
                else -> 2
            }
            drawReflectionsFromAim(canvas, aimX, aimY, aimX - cueX, aimY - cueY, bounceCount)

            if (!isPlayMode) {
                drawRingHandle(canvas, cueX, cueY, 15f, Color.WHITE)
                drawRingHandle(canvas, aimX, aimY, aimRingRadius, 0xFF00FF55.toInt())

                drawRingHandle(canvas, leftWall, topWall, 16f, 0xFF0088FF.toInt())
                drawRingHandle(canvas, rightWall, topWall, 16f, 0xFF0088FF.toInt())
                drawRingHandle(canvas, leftWall, bottomWall, 16f, 0xFF0088FF.toInt())
                drawRingHandle(canvas, rightWall, bottomWall, 16f, 0xFF0088FF.toInt())
            }
        }

        private fun drawRingHandle(canvas: Canvas, x: Float, y: Float, radius: Float, strokeColor: Int) {
            handlePaint.color = strokeColor
            canvas.drawCircle(x, y, radius, handlePaint)
        }

        private fun drawReflectionsFromAim(
            canvas: Canvas,
            startX: Float,
            startY: Float,
            dirX: Float,
            dirY: Float,
            bounces: Int
        ) {
            if (bounces <= 0) return

            var x = startX.coerceIn(leftWall, rightWall)
            var y = startY.coerceIn(topWall, bottomWall)
            var vx = dirX
            var vy = dirY

            val len = hypot(vx, vy)
            if (len < 0.001f) return
            vx /= len
            vy /= len

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

                val paintForBounce = when (i) {
                    0 -> line2Paint
                    1 -> line3Paint
                    2 -> line4Paint
                    else -> line5Paint
                }

                canvas.drawLine(x, y, nextX, nextY, paintForBounce)

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

                    preserveBallsExact()
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
