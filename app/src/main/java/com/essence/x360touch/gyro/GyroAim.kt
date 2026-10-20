package com.essence.x360touch.gyro

import android.content.Context
import android.content.SharedPreferences
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.abs

/**
 * Gyroscope aiming: turns the phone's angular velocity into right-stick deflection,
 * which is how the game reads camera rotation.
 */
class GyroAim(context: Context, private val prefs: SharedPreferences) : SensorEventListener {

    enum class Mode(val label: String) {
        OFF("Apagado"),
        ALWAYS("Siempre activo"),
        WHILE_AIMING("Solo al apuntar (LT)"),
        TOGGLE("Con el botón GIRO")
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val sensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    val available: Boolean get() = sensor != null

    var mode: Mode = runCatching { Mode.valueOf(prefs.getString(K_MODE, null) ?: "") }
        .getOrDefault(Mode.WHILE_AIMING)
        set(value) { field = value; save() }

    /** Stick deflection per rad/s. */
    var sensitivity: Float = prefs.getFloat(K_SENS, 1.0f)
        set(value) { field = value.coerceIn(0.1f, 4f); save() }

    var invertX: Boolean = prefs.getBoolean(K_INV_X, false)
        set(value) { field = value; save() }

    var invertY: Boolean = prefs.getBoolean(K_INV_Y, false)
        set(value) { field = value; save() }

    /** Ignores hand tremor below this speed (rad/s). */
    var deadzone: Float = 0.03f

    var toggledOn: Boolean = false

    /** Returns true while the player aims (LT held). Set by the activity. */
    var isAiming: () -> Boolean = { false }

    /** Current display rotation (Surface.ROTATION_*). Set by the activity. */
    var rotation: () -> Int = { Surface.ROTATION_90 }

    @Volatile var outX: Float = 0f
        private set
    @Volatile var outY: Float = 0f
        private set

    private var running = false

    fun isActive(): Boolean = available && when (mode) {
        Mode.OFF -> false
        Mode.ALWAYS -> true
        Mode.WHILE_AIMING -> isAiming()
        Mode.TOGGLE -> toggledOn
    }

    fun start() {
        if (running || sensor == null || mode == Mode.OFF) return
        sensorManager?.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)
        running = true
    }

    fun stop() {
        if (running) sensorManager?.unregisterListener(this)
        running = false
        outX = 0f
        outY = 0f
    }

    /** Applies a mode change: starts or stops the sensor as needed. */
    fun refresh() {
        if (mode == Mode.OFF) stop() else start()
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (!isActive()) {
            outX = 0f; outY = 0f
            return
        }
        val gx = event.values[0]
        val gy = event.values[1]
        // In landscape the device X axis is vertical on screen (yaw) and Y is horizontal (pitch).
        var yaw: Float
        var pitch: Float
        when (rotation()) {
            Surface.ROTATION_270 -> { yaw = gx; pitch = -gy }
            Surface.ROTATION_0, Surface.ROTATION_180 -> { yaw = -event.values[1]; pitch = event.values[0] }
            else -> { yaw = -gx; pitch = gy }
        }
        if (abs(yaw) < deadzone) yaw = 0f
        if (abs(pitch) < deadzone) pitch = 0f
        var x = yaw * sensitivity * 0.5f
        var y = pitch * sensitivity * 0.5f
        if (invertX) x = -x
        if (invertY) y = -y
        outX = x.coerceIn(-1f, 1f)
        outY = y.coerceIn(-1f, 1f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun save() {
        prefs.edit()
            .putString(K_MODE, mode.name)
            .putFloat(K_SENS, sensitivity)
            .putBoolean(K_INV_X, invertX)
            .putBoolean(K_INV_Y, invertY)
            .apply()
    }

    companion object {
        private const val K_MODE = "gyro_mode"
        private const val K_SENS = "gyro_sens"
        private const val K_INV_X = "gyro_inv_x"
        private const val K_INV_Y = "gyro_inv_y"
    }
}
