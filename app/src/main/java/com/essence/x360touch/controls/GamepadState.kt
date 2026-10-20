package com.essence.x360touch.controls

/** XInput button bits (same values as XINPUT_GAMEPAD_* on Xbox 360). */
object Buttons {
    const val DPAD_UP = 0x0001
    const val DPAD_DOWN = 0x0002
    const val DPAD_LEFT = 0x0004
    const val DPAD_RIGHT = 0x0008
    const val START = 0x0010
    const val BACK = 0x0020
    const val L3 = 0x0040
    const val R3 = 0x0080
    const val LB = 0x0100
    const val RB = 0x0200
    const val A = 0x1000
    const val B = 0x2000
    const val X = 0x4000
    const val Y = 0x8000
}

/**
 * Virtual Xbox 360 pad.
 * Sticks: -1..1 (Y positive = up, as XInput). Triggers: 0..1.
 */
class GamepadState {
    var buttons: Int = 0
    var lx: Float = 0f
    var ly: Float = 0f
    var rx: Float = 0f
    var ry: Float = 0f
    var lt: Float = 0f
    var rt: Float = 0f

    fun reset() {
        buttons = 0
        lx = 0f; ly = 0f; rx = 0f; ry = 0f; lt = 0f; rt = 0f
    }

    fun copyFrom(o: GamepadState) {
        buttons = o.buttons
        lx = o.lx; ly = o.ly; rx = o.rx; ry = o.ry; lt = o.lt; rt = o.rt
    }

    /** XInput integer ranges, ready for SDL/Xenia: sticks -32768..32767, triggers 0..255. */
    fun stickToShort(v: Float): Short = (v.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
    fun triggerToByte(v: Float): Int = (v.coerceIn(0f, 1f) * 255f).toInt()
}
