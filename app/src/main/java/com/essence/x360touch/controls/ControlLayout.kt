package com.essence.x360touch.controls

import org.json.JSONArray
import org.json.JSONObject

enum class ElementType { BUTTON, TRIGGER, STICK, DPAD, GYRO_TOGGLE }

enum class Binding(val bit: Int) {
    A(Buttons.A), B(Buttons.B), X(Buttons.X), Y(Buttons.Y),
    LB(Buttons.LB), RB(Buttons.RB),
    START(Buttons.START), BACK(Buttons.BACK),
    L3(Buttons.L3), R3(Buttons.R3),
    LT(0), RT(0),
    LEFT_STICK(0), RIGHT_STICK(0),
    DPAD(0),
    GYRO(0)
}

/**
 * One on-screen control. Position is normalized to the view (0..1),
 * size is the diameter as a fraction of the view's shorter side.
 */
data class ControlElement(
    val id: String,
    val type: ElementType,
    val binding: Binding,
    val label: String,
    var cx: Float,
    var cy: Float,
    var size: Float,
    var opacity: Float = 0.55f,
    var visible: Boolean = true
)

object ControlLayout {
    const val VERSION = 1
    const val MIN_SIZE = 0.05f
    const val MAX_SIZE = 0.55f

    /** Shooter layout (like PUBG Mobile) with Gears of War actions. Every control stays editable. */
    fun default(): MutableList<ControlElement> = shooter()

    fun shooter(): MutableList<ControlElement> = mutableListOf(
        ControlElement("ls", ElementType.STICK, Binding.LEFT_STICK, "MOVER", 0.14f, 0.68f, 0.32f),
        ControlElement("rt", ElementType.TRIGGER, Binding.RT, "DISPARAR", 0.86f, 0.60f, 0.18f),
        ControlElement("rt_l", ElementType.TRIGGER, Binding.RT, "DISPARAR", 0.08f, 0.30f, 0.11f),
        ControlElement("lt", ElementType.TRIGGER, Binding.LT, "APUNTAR", 0.95f, 0.36f, 0.12f),
        ControlElement("a", ElementType.BUTTON, Binding.A, "CUBRIRSE", 0.72f, 0.84f, 0.13f),
        ControlElement("b", ElementType.BUTTON, Binding.B, "GOLPE", 0.95f, 0.82f, 0.11f),
        ControlElement("rb", ElementType.BUTTON, Binding.RB, "RECARGAR", 0.70f, 0.56f, 0.10f),
        ControlElement("x", ElementType.BUTTON, Binding.X, "USAR", 0.58f, 0.42f, 0.10f),
        ControlElement("y", ElementType.BUTTON, Binding.Y, "MIRAR", 0.80f, 0.18f, 0.09f),
        ControlElement("r3", ElementType.BUTTON, Binding.R3, "ZOOM", 0.68f, 0.26f, 0.08f),
        ControlElement("dpad", ElementType.DPAD, Binding.DPAD, "", 0.32f, 0.88f, 0.17f),
        ControlElement("lb", ElementType.BUTTON, Binding.LB, "LB", 0.08f, 0.10f, 0.08f),
        ControlElement("start", ElementType.BUTTON, Binding.START, "PAUSA", 0.95f, 0.10f, 0.08f),
        ControlElement("back", ElementType.BUTTON, Binding.BACK, "BACK", 0.20f, 0.10f, 0.08f, visible = false),
        ControlElement("l3", ElementType.BUTTON, Binding.L3, "L3", 0.28f, 0.50f, 0.08f, visible = false),
        ControlElement("rs", ElementType.STICK, Binding.RIGHT_STICK, "CÁMARA", 0.62f, 0.80f, 0.22f, visible = false),
        ControlElement("gyro", ElementType.GYRO_TOGGLE, Binding.GYRO, "GIRO", 0.50f, 0.14f, 0.08f)
    )

    /** Classic Xbox 360 pad layout. */
    fun xboxPad(): MutableList<ControlElement> = mutableListOf(
        ControlElement("ls", ElementType.STICK, Binding.LEFT_STICK, "LS", 0.14f, 0.70f, 0.30f),
        ControlElement("rs", ElementType.STICK, Binding.RIGHT_STICK, "RS", 0.66f, 0.80f, 0.22f),
        ControlElement("dpad", ElementType.DPAD, Binding.DPAD, "", 0.32f, 0.86f, 0.18f),
        ControlElement("a", ElementType.BUTTON, Binding.A, "A", 0.87f, 0.80f, 0.12f),
        ControlElement("b", ElementType.BUTTON, Binding.B, "B", 0.95f, 0.64f, 0.12f),
        ControlElement("x", ElementType.BUTTON, Binding.X, "X", 0.79f, 0.64f, 0.12f),
        ControlElement("y", ElementType.BUTTON, Binding.Y, "Y", 0.87f, 0.48f, 0.12f),
        ControlElement("lt", ElementType.TRIGGER, Binding.LT, "LT", 0.06f, 0.24f, 0.13f),
        ControlElement("lb", ElementType.BUTTON, Binding.LB, "LB", 0.17f, 0.15f, 0.11f),
        ControlElement("rt", ElementType.TRIGGER, Binding.RT, "RT", 0.94f, 0.24f, 0.13f),
        ControlElement("rb", ElementType.BUTTON, Binding.RB, "RB", 0.83f, 0.15f, 0.11f),
        ControlElement("back", ElementType.BUTTON, Binding.BACK, "BACK", 0.42f, 0.10f, 0.09f),
        ControlElement("start", ElementType.BUTTON, Binding.START, "START", 0.58f, 0.10f, 0.09f),
        ControlElement("l3", ElementType.BUTTON, Binding.L3, "L3", 0.28f, 0.52f, 0.09f),
        ControlElement("r3", ElementType.BUTTON, Binding.R3, "R3", 0.54f, 0.64f, 0.09f),
        ControlElement("gyro", ElementType.GYRO_TOGGLE, Binding.GYRO, "GIRO", 0.50f, 0.30f, 0.10f)
    )

    fun toJson(list: List<ControlElement>): String {
        val arr = JSONArray()
        for (e in list) {
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("type", e.type.name)
                    .put("binding", e.binding.name)
                    .put("label", e.label)
                    .put("cx", e.cx.toDouble())
                    .put("cy", e.cy.toDouble())
                    .put("size", e.size.toDouble())
                    .put("opacity", e.opacity.toDouble())
                    .put("visible", e.visible)
            )
        }
        return JSONObject().put("version", VERSION).put("elements", arr).toString()
    }

    /** Returns null when the JSON is invalid, so the caller falls back to the default layout. */
    fun fromJson(json: String?): MutableList<ControlElement>? {
        if (json.isNullOrBlank()) return null
        return try {
            val root = JSONObject(json)
            val arr = root.getJSONArray("elements")
            val out = mutableListOf<ControlElement>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out += ControlElement(
                    id = o.getString("id"),
                    type = ElementType.valueOf(o.getString("type")),
                    binding = Binding.valueOf(o.getString("binding")),
                    label = o.optString("label", ""),
                    cx = o.getDouble("cx").toFloat().coerceIn(0f, 1f),
                    cy = o.getDouble("cy").toFloat().coerceIn(0f, 1f),
                    size = o.getDouble("size").toFloat().coerceIn(MIN_SIZE, MAX_SIZE),
                    opacity = o.optDouble("opacity", 0.55).toFloat().coerceIn(0.05f, 1f),
                    visible = o.optBoolean("visible", true)
                )
            }
            // Controls added in newer versions appear with their default position.
            val ids = out.map { it.id }.toSet()
            default().filter { it.id !in ids }.forEach { out += it }
            out
        } catch (t: Throwable) {
            null
        }
    }
}
