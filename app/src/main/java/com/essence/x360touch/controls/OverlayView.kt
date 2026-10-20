package com.essence.x360touch.controls

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.PointF
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.min

/**
 * Multi-touch virtual Xbox 360 pad.
 * Play mode: produces [state]. Edit mode: drag to move, pinch to resize the selected control.
 */
class OverlayView(context: Context) : View(context) {

    var elements: MutableList<ControlElement> = ControlLayout.default()
        set(value) {
            field = value
            selected = null
            releaseAll()
        }

    /** Current pad state from touch only (gyro is merged by the caller). */
    val state = GamepadState()

    var onStateChanged: ((GamepadState) -> Unit)? = null
    var onSelectionChanged: ((ControlElement?) -> Unit)? = null
    var onGyroToggle: (() -> Unit)? = null

    var editMode: Boolean = false
        set(value) {
            field = value
            selected = null
            releaseAll()
        }

    var selected: ControlElement? = null
        private set(value) {
            field = value
            onSelectionChanged?.invoke(value)
            invalidate()
        }

    /** Drawn as highlighted on the gyro toggle button. */
    var gyroActive: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    // pointerId -> control it grabbed, and its last position
    private val grabbed = HashMap<Int, ControlElement>()
    private val positions = HashMap<Int, PointF>()
    // stick id -> knob offset (-1..1, screen coordinates) for drawing
    private val knobs = HashMap<String, PointF>()

    /** Swipe-to-look (PUBG style): a finger on empty screen, right of [lookZoneStart], moves the camera. */
    var lookEnabled: Boolean = true
    /** Fraction of the width where the look zone starts (0.4 = right 60% of the screen). */
    var lookZoneStart: Float = 0.4f
    /** Dragging while holding RT (fire) also moves the camera, like the fire button in mobile shooters. */
    var fireDragLook: Boolean = true
    private var lookPointer = -1
    private val lookLast = PointF()
    private var lookDx = 0f
    private var lookDy = 0f

    /** Pixels moved by look fingers since the last call; resets the accumulator. */
    fun consumeLook(out: PointF) {
        out.set(lookDx, lookDy)
        lookDx = 0f
        lookDy = 0f
    }

    // edit-mode drag
    private var dragPointer = -1
    private var dragOffsetX = 0f
    private var dragOffsetY = 0f

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = 14f * resources.displayMetrics.scaledDensity
    }

    private val scaleDetector = ScaleGestureDetector(context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val e = selected ?: return false
                e.size = (e.size * detector.scaleFactor)
                    .coerceIn(ControlLayout.MIN_SIZE, ControlLayout.MAX_SIZE)
                invalidate()
                return true
            }
        })

    init {
        isHapticFeedbackEnabled = true
    }

    // ---------- geometry ----------

    private fun shortSide(): Float = min(width, height).toFloat().coerceAtLeast(1f)
    private fun radius(e: ControlElement): Float = e.size * shortSide() / 2f
    private fun centerX(e: ControlElement): Float = e.cx * width
    private fun centerY(e: ControlElement): Float = e.cy * height

    private fun hitTest(x: Float, y: Float): ControlElement? {
        var best: ControlElement? = null
        var bestDist = Float.MAX_VALUE
        for (e in elements) {
            if (!e.visible && !editMode) continue
            val slack = when (e.type) {
                ElementType.STICK -> 1.35f
                ElementType.DPAD -> 1.25f
                else -> 1.15f
            }
            val d = hypot(x - centerX(e), y - centerY(e))
            if (d <= radius(e) * slack && d < bestDist) {
                best = e
                bestDist = d
            }
        }
        return best
    }

    // ---------- touch ----------

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        return if (editMode) handleEdit(ev) else handlePlay(ev)
    }

    private fun handlePlay(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                val i = ev.actionIndex
                val id = ev.getPointerId(i)
                val x = ev.getX(i)
                val y = ev.getY(i)
                val e = hitTest(x, y)
                if (e == null && lookEnabled && lookPointer == -1 && x >= width * lookZoneStart) {
                    lookPointer = id
                    lookLast.set(x, y)
                }
                if (e != null) {
                    grabbed[id] = e
                    positions[id] = PointF(x, y)
                    if (e.type == ElementType.GYRO_TOGGLE) onGyroToggle?.invoke()
                    if (e.type == ElementType.BUTTON || e.type == ElementType.TRIGGER) {
                        performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                for (i in 0 until ev.pointerCount) {
                    val id = ev.getPointerId(i)
                    val x = ev.getX(i)
                    val y = ev.getY(i)
                    if (id == lookPointer) {
                        lookDx += x - lookLast.x
                        lookDy += y - lookLast.y
                        lookLast.set(x, y)
                    }
                    val p = positions[id]
                    if (p != null && fireDragLook && lookEnabled && grabbed[id]?.binding == Binding.RT) {
                        lookDx += x - p.x
                        lookDy += y - p.y
                    }
                    p?.set(x, y)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = ev.getPointerId(ev.actionIndex)
                grabbed.remove(id)
                positions.remove(id)
                if (id == lookPointer) lookPointer = -1
            }
            MotionEvent.ACTION_CANCEL -> {
                grabbed.clear()
                positions.clear()
                lookPointer = -1
            }
        }
        recompute()
        return true
    }

    private fun handleEdit(ev: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val e = hitTest(ev.x, ev.y)
                selected = e
                if (e != null) {
                    dragPointer = ev.getPointerId(0)
                    dragOffsetX = centerX(e) - ev.x
                    dragOffsetY = centerY(e) - ev.y
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> dragPointer = -1 // pinch: stop moving
            MotionEvent.ACTION_MOVE -> {
                val e = selected
                if (e != null && dragPointer != -1 && !scaleDetector.isInProgress) {
                    val i = ev.findPointerIndex(dragPointer)
                    if (i >= 0) {
                        e.cx = ((ev.getX(i) + dragOffsetX) / width).coerceIn(0f, 1f)
                        e.cy = ((ev.getY(i) + dragOffsetY) / height).coerceIn(0f, 1f)
                        invalidate()
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragPointer = -1
        }
        return true
    }

    private fun releaseAll() {
        grabbed.clear()
        positions.clear()
        lookPointer = -1
        lookDx = 0f
        lookDy = 0f
        knobs.clear()
        state.reset()
        onStateChanged?.invoke(state)
        invalidate()
    }

    /** Rebuilds the pad state from every finger currently on a control. */
    private fun recompute() {
        state.reset()
        knobs.clear()
        for ((id, e) in grabbed) {
            val p = positions[id] ?: continue
            val r = radius(e).coerceAtLeast(1f)
            when (e.type) {
                ElementType.BUTTON -> state.buttons = state.buttons or e.binding.bit
                ElementType.TRIGGER -> if (e.binding == Binding.LT) state.lt = 1f else state.rt = 1f
                ElementType.STICK -> {
                    var dx = (p.x - centerX(e)) / r
                    var dy = (p.y - centerY(e)) / r
                    val len = hypot(dx, dy)
                    if (len > 1f) { dx /= len; dy /= len }
                    val dead = 0.08f
                    val ox = if (len < dead) 0f else dx
                    val oy = if (len < dead) 0f else dy
                    knobs[e.id] = PointF(dx, dy)
                    if (e.binding == Binding.LEFT_STICK) {
                        state.lx = ox; state.ly = -oy
                    } else {
                        state.rx = ox; state.ry = -oy
                    }
                }
                ElementType.DPAD -> {
                    val dx = (p.x - centerX(e)) / r
                    val dy = (p.y - centerY(e)) / r
                    if (hypot(dx, dy) >= 0.25f) {
                        val ang = Math.toDegrees(atan2(-dy, dx).toDouble())
                        if (ang > -67.5 && ang < 67.5) state.buttons = state.buttons or Buttons.DPAD_RIGHT
                        if (ang > 22.5 && ang < 157.5) state.buttons = state.buttons or Buttons.DPAD_UP
                        if (ang > 112.5 || ang < -112.5) state.buttons = state.buttons or Buttons.DPAD_LEFT
                        if (ang > -157.5 && ang < -22.5) state.buttons = state.buttons or Buttons.DPAD_DOWN
                    }
                }
                ElementType.GYRO_TOGGLE -> Unit
            }
        }
        onStateChanged?.invoke(state)
        invalidate()
    }

    // ---------- drawing ----------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val held = grabbed.values.toSet()
        for (e in elements) {
            if (!e.visible && !editMode) continue
            val cx = centerX(e)
            val cy = centerY(e)
            val r = radius(e)
            val pressed = e in held
            var a = e.opacity
            if (editMode) a = if (e.visible) a.coerceAtLeast(0.35f) else 0.15f
            val alpha = (a * 255).toInt().coerceIn(0, 255)

            val base = when {
                e.type == ElementType.GYRO_TOGGLE && gyroActive -> Color.rgb(40, 170, 90)
                pressed -> Color.rgb(90, 90, 90)
                else -> Color.rgb(25, 25, 25)
            }
            fill.color = base
            fill.alpha = alpha
            stroke.color = if (editMode && e == selected) Color.YELLOW
                else if (e.binding == Binding.RT) Color.rgb(230, 80, 60) else accent(e)
            stroke.alpha = if (editMode && e == selected) 255 else alpha

            when (e.type) {
                ElementType.STICK -> {
                    canvas.drawCircle(cx, cy, r, fill)
                    canvas.drawCircle(cx, cy, r, stroke)
                    val k = knobs[e.id]
                    val kx = cx + (k?.x ?: 0f) * r
                    val ky = cy + (k?.y ?: 0f) * r
                    fill.color = Color.rgb(170, 170, 170)
                    fill.alpha = alpha
                    canvas.drawCircle(kx, ky, r * 0.42f, fill)
                }
                ElementType.DPAD -> {
                    canvas.drawCircle(cx, cy, r, fill)
                    canvas.drawCircle(cx, cy, r, stroke)
                    text.textSize = r * 0.45f
                    text.alpha = alpha
                    val o = r * 0.6f
                    val dy = text.textSize / 3f
                    canvas.drawText("▲", cx, cy - o + dy, text)
                    canvas.drawText("▼", cx, cy + o + dy, text)
                    canvas.drawText("◀", cx - o, cy + dy, text)
                    canvas.drawText("▶", cx + o, cy + dy, text)
                }
                else -> {
                    canvas.drawCircle(cx, cy, r, fill)
                    canvas.drawCircle(cx, cy, r, stroke)
                    val icon = iconFor(e)
                    if (icon != null) {
                        // Action icon with a small caption underneath (shooter layout).
                        iconPaint.alpha = alpha
                        iconPaint.strokeWidth = (r * 0.09f).coerceAtLeast(2f)
                        drawIcon(canvas, icon, cx, cy - r * 0.14f, r * 0.42f)
                        text.textSize = r * min(0.28f, 1.9f / e.label.length.coerceAtLeast(1))
                        text.alpha = alpha
                        canvas.drawText(e.label, cx, cy + r * 0.62f, text)
                    } else {
                        text.textSize = r * min(0.75f, 2.4f / e.label.length.coerceAtLeast(1))
                        text.alpha = alpha
                        canvas.drawText(e.label, cx, cy + text.textSize / 3f, text)
                    }
                }
            }
        }
        if (editMode) {
            canvas.drawText(
                "Arrastra para mover · Pellizca para cambiar tamaño · Toca un control para seleccionarlo",
                width / 2f, height * 0.94f, hint
            )
        }
    }

    // ---------- action icons (drawn with paths, no image files) ----------

    private enum class Icon { FIRE, AIM, COVER, MELEE, RELOAD, USE, LOOK, ZOOM, PAUSE, GYRO }

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val iconPath = Path()
    private val iconRect = RectF()

    /** Only action-named controls (shooter layout) get icons; letter buttons keep their letter. */
    private fun iconFor(e: ControlElement): Icon? {
        if (e.label.length <= 2) return null
        return when (e.binding) {
            Binding.RT -> Icon.FIRE
            Binding.LT -> Icon.AIM
            Binding.A -> Icon.COVER
            Binding.B -> Icon.MELEE
            Binding.RB -> Icon.RELOAD
            Binding.X -> Icon.USE
            Binding.Y -> Icon.LOOK
            Binding.R3 -> Icon.ZOOM
            Binding.START -> Icon.PAUSE
            Binding.GYRO -> Icon.GYRO
            else -> null
        }
    }

    /** Draws [icon] centered at (x, y) inside a box of half-size [s]. */
    private fun drawIcon(c: Canvas, icon: Icon, x: Float, y: Float, s: Float) {
        val p = iconPaint
        val path = iconPath
        path.reset()
        when (icon) {
            Icon.FIRE -> { // crosshair
                c.drawCircle(x, y, s * 0.62f, p)
                c.drawLine(x, y - s, x, y - s * 0.3f, p)
                c.drawLine(x, y + s * 0.3f, x, y + s, p)
                c.drawLine(x - s, y, x - s * 0.3f, y, p)
                c.drawLine(x + s * 0.3f, y, x + s, y, p)
                p.style = Paint.Style.FILL
                c.drawCircle(x, y, s * 0.1f, p)
                p.style = Paint.Style.STROKE
            }
            Icon.AIM -> { // scope: circle + fine cross
                c.drawCircle(x, y, s * 0.9f, p)
                c.drawLine(x - s * 0.9f, y, x + s * 0.9f, y, p)
                c.drawLine(x, y - s * 0.9f, x, y + s * 0.9f, p)
                c.drawCircle(x, y, s * 0.3f, p)
            }
            Icon.COVER -> { // shield
                path.moveTo(x, y - s)
                path.lineTo(x + s * 0.85f, y - s * 0.65f)
                path.lineTo(x + s * 0.75f, y + s * 0.2f)
                path.quadTo(x + s * 0.5f, y + s * 0.75f, x, y + s)
                path.quadTo(x - s * 0.5f, y + s * 0.75f, x - s * 0.75f, y + s * 0.2f)
                path.lineTo(x - s * 0.85f, y - s * 0.65f)
                path.close()
                c.drawPath(path, p)
            }
            Icon.MELEE -> { // impact burst
                val n = 8
                for (i in 0 until n * 2) {
                    val a = Math.PI * i / n - Math.PI / 2
                    val rr = if (i % 2 == 0) s else s * 0.45f
                    val px = x + (rr * Math.cos(a)).toFloat()
                    val py = y + (rr * Math.sin(a)).toFloat()
                    if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                c.drawPath(path, p)
            }
            Icon.RELOAD -> { // circular arrow
                iconRect.set(x - s * 0.8f, y - s * 0.8f, x + s * 0.8f, y + s * 0.8f)
                c.drawArc(iconRect, -60f, 300f, false, p)
                val ax = x + s * 0.4f
                val ay = y - s * 0.69f
                path.moveTo(ax - s * 0.35f, ay - s * 0.3f)
                path.lineTo(ax + s * 0.05f, ay)
                path.lineTo(ax - s * 0.3f, ay + s * 0.35f)
                c.drawPath(path, p)
            }
            Icon.USE -> { // hand pointing up (simplified): arrow into a box
                iconRect.set(x - s * 0.8f, y - s * 0.1f, x + s * 0.8f, y + s * 0.9f)
                c.drawRoundRect(iconRect, s * 0.15f, s * 0.15f, p)
                c.drawLine(x, y + s * 0.5f, x, y - s, p)
                path.moveTo(x - s * 0.35f, y - s * 0.6f)
                path.lineTo(x, y - s)
                path.lineTo(x + s * 0.35f, y - s * 0.6f)
                c.drawPath(path, p)
            }
            Icon.LOOK -> { // eye
                path.moveTo(x - s, y)
                path.quadTo(x, y - s * 1.1f, x + s, y)
                path.quadTo(x, y + s * 1.1f, x - s, y)
                path.close()
                c.drawPath(path, p)
                c.drawCircle(x, y, s * 0.3f, p)
            }
            Icon.ZOOM -> { // magnifier with +
                c.drawCircle(x - s * 0.2f, y - s * 0.2f, s * 0.6f, p)
                c.drawLine(x + s * 0.25f, y + s * 0.25f, x + s * 0.9f, y + s * 0.9f, p)
                c.drawLine(x - s * 0.5f, y - s * 0.2f, x + s * 0.1f, y - s * 0.2f, p)
                c.drawLine(x - s * 0.2f, y - s * 0.5f, x - s * 0.2f, y + s * 0.1f, p)
            }
            Icon.PAUSE -> {
                c.drawLine(x - s * 0.35f, y - s * 0.7f, x - s * 0.35f, y + s * 0.7f, p)
                c.drawLine(x + s * 0.35f, y - s * 0.7f, x + s * 0.35f, y + s * 0.7f, p)
            }
            Icon.GYRO -> { // tilted phone with motion arcs
                iconRect.set(x - s * 0.35f, y - s * 0.75f, x + s * 0.35f, y + s * 0.75f)
                c.save()
                c.rotate(-20f, x, y)
                c.drawRoundRect(iconRect, s * 0.12f, s * 0.12f, p)
                c.restore()
                iconRect.set(x - s, y - s, x + s, y + s)
                c.drawArc(iconRect, 200f, 50f, false, p)
                c.drawArc(iconRect, 20f, 50f, false, p)
            }
        }
    }

    private fun accent(e: ControlElement): Int = when (e.binding) {
        Binding.A -> Color.rgb(96, 186, 70)
        Binding.B -> Color.rgb(219, 52, 44)
        Binding.X -> Color.rgb(36, 120, 220)
        Binding.Y -> Color.rgb(240, 190, 30)
        else -> Color.rgb(200, 200, 200)
    }
}
