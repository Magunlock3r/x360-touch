package com.essence.x360touch.controls

import org.json.JSONArray
import org.json.JSONObject

enum class ElementType { BUTTON, TRIGGER, STICK, DPAD, GYRO_TOGGLE, MENU, HIDE_UI }

enum class Binding(val bit: Int) {
    A(Buttons.A), B(Buttons.B), X(Buttons.X), Y(Buttons.Y),
    LB(Buttons.LB), RB(Buttons.RB),
    START(Buttons.START), BACK(Buttons.BACK),
    L3(Buttons.L3), R3(Buttons.R3),
    LT(0), RT(0),
    LEFT_STICK(0), RIGHT_STICK(0),
    DPAD(0),
    DPAD_UP(Buttons.DPAD_UP), DPAD_DOWN(Buttons.DPAD_DOWN),
    DPAD_LEFT(Buttons.DPAD_LEFT), DPAD_RIGHT(Buttons.DPAD_RIGHT),
    GYRO(0),
    MENU(0), HIDE_UI(0)
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

    /**
     * Shooter layout, like the touch HUD of mobile shooters: icon-only circles with a thin outline.
     * Gears of War mapping (StrategyWiki): RT shoot, LT aim, A cover/run/evade, B melee (hold: chainsaw),
     * X use, Y look, RB reload (active reload), LB tac-com, R3 zoom, L3 crouch, D-pad weapons.
     */
    fun shooter(): MutableList<ControlElement> = mutableListOf(
        ControlElement("ls", ElementType.STICK, Binding.LEFT_STICK, "Mover", 0.15f, 0.72f, 0.26f, opacity = 0.35f),
        // top row (system)
        ControlElement("menu", ElementType.MENU, Binding.MENU, "Menú", 0.44f, 0.09f, 0.075f, opacity = 0.35f),
        ControlElement("hide", ElementType.HIDE_UI, Binding.HIDE_UI, "Ocultar", 0.50f, 0.09f, 0.075f, opacity = 0.35f),
        ControlElement("start", ElementType.BUTTON, Binding.START, "Pausa", 0.56f, 0.09f, 0.075f, opacity = 0.35f),
        ControlElement("lb", ElementType.BUTTON, Binding.LB, "Objetivo", 0.62f, 0.09f, 0.075f, opacity = 0.35f),
        // top-left: aim ; top-right: fire
        ControlElement("lt", ElementType.TRIGGER, Binding.LT, "Apuntar", 0.18f, 0.16f, 0.15f, opacity = 0.35f),
        ControlElement("rt", ElementType.TRIGGER, Binding.RT, "Disparar", 0.82f, 0.20f, 0.20f, opacity = 0.35f),
        ControlElement("rt_l", ElementType.TRIGGER, Binding.RT, "Disparar", 0.06f, 0.42f, 0.11f, opacity = 0.35f),
        // right cluster (actions)
        ControlElement("dpad_up", ElementType.BUTTON, Binding.DPAD_UP, "Granada", 0.85f, 0.46f, 0.11f, opacity = 0.35f),
        ControlElement("b", ElementType.BUTTON, Binding.B, "Golpe", 0.93f, 0.56f, 0.11f, opacity = 0.35f),
        ControlElement("a", ElementType.BUTTON, Binding.A, "Cubrirse", 0.78f, 0.58f, 0.11f, opacity = 0.35f),
        ControlElement("dpad", ElementType.DPAD, Binding.DPAD, "Armas", 0.70f, 0.68f, 0.13f, opacity = 0.35f),
        ControlElement("rb", ElementType.BUTTON, Binding.RB, "Recargar", 0.78f, 0.78f, 0.11f, opacity = 0.35f),
        ControlElement("x", ElementType.BUTTON, Binding.X, "Usar", 0.86f, 0.68f, 0.11f, opacity = 0.35f),
        ControlElement("l3", ElementType.BUTTON, Binding.L3, "Agacharse", 0.93f, 0.80f, 0.10f, opacity = 0.35f),
        ControlElement("r3", ElementType.BUTTON, Binding.R3, "Zoom", 0.93f, 0.38f, 0.09f, opacity = 0.35f),
        // bottom center
        ControlElement("y", ElementType.BUTTON, Binding.Y, "Mirar", 0.44f, 0.90f, 0.09f, opacity = 0.35f),
        ControlElement("gyro", ElementType.GYRO_TOGGLE, Binding.GYRO, "Giro", 0.51f, 0.90f, 0.09f, opacity = 0.35f),
        ControlElement("back", ElementType.BUTTON, Binding.BACK, "Back", 0.58f, 0.90f, 0.09f, opacity = 0.35f, visible = false),
        ControlElement("rs", ElementType.STICK, Binding.RIGHT_STICK, "Cámara", 0.62f, 0.80f, 0.22f, visible = false)
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
