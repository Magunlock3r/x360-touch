package com.essence.x360touch.controls

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
                    positions[id]?.set(ev.getX(i), ev.getY(i))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                val id = ev.getPointerId(ev.actionIndex)
                grabbed.remove(id)
                positions.remove(id)
            }
            MotionEvent.ACTION_CANCEL -> {
                grabbed.clear()
                positions.clear()
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
            stroke.color = if (editMode && e == selected) Color.YELLOW else accent(e)
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
                    text.textSize = (r * if (e.label.length > 2) 0.42f else 0.75f)
                    text.alpha = alpha
                    canvas.drawText(e.label, cx, cy + text.textSize / 3f, text)
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

    private fun accent(e: ControlElement): Int = when (e.binding) {
        Binding.A -> Color.rgb(96, 186, 70)
        Binding.B -> Color.rgb(219, 52, 44)
        Binding.X -> Color.rgb(36, 120, 220)
        Binding.Y -> Color.rgb(240, 190, 30)
        else -> Color.rgb(200, 200, 200)
    }
}
