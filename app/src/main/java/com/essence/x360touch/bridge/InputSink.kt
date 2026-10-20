package com.essence.x360touch.bridge

import com.essence.x360touch.controls.GamepadState

/**
 * Where the final pad state goes once per frame.
 * In the Gears port this forwards to native code, which feeds an SDL3 virtual joystick
 * (SDL_AttachVirtualJoystick + SDL_SetJoystickVirtualAxis/Button) or the runtime's XInputGetState.
 */
fun interface InputSink {
    fun onGamepad(state: GamepadState)
}

/**
 * JNI bridge for the port. Not loaded in this demo (no native library yet);
 * the port's native code implements Java_com_essence_x360touch_bridge_NativeInputSink_nativeSend.
 */
class NativeInputSink : InputSink {
    override fun onGamepad(state: GamepadState) {
        nativeSend(
            state.buttons,
            state.stickToShort(state.lx).toInt(), state.stickToShort(state.ly).toInt(),
            state.stickToShort(state.rx).toInt(), state.stickToShort(state.ry).toInt(),
            state.triggerToByte(state.lt), state.triggerToByte(state.rt)
        )
    }

    private external fun nativeSend(
        buttons: Int, lx: Int, ly: Int, rx: Int, ry: Int, lt: Int, rt: Int
    )
}
