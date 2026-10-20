package com.essence.x360touch

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.PointF
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import com.essence.x360touch.bridge.InputSink
import com.essence.x360touch.cheats.CheatManager
import com.essence.x360touch.controls.ControlElement
import com.essence.x360touch.controls.ControlLayout
import com.essence.x360touch.controls.GamepadState
import com.essence.x360touch.controls.OverlayView
import com.essence.x360touch.game.GameFile
import com.essence.x360touch.gyro.GyroAim
import kotlin.concurrent.thread

class MainActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var overlay: OverlayView
    private lateinit var gyro: GyroAim
    private lateinit var cheats: CheatManager
    private lateinit var hud: TextView
    private lateinit var editButton: Button
    private lateinit var opacityRow: LinearLayout
    private lateinit var opacityBar: SeekBar
    private lateinit var gameFile: GameFile

    /** Game shown in the HUD, e.g. "Gears of War.iso (6.8 GB) ✓". */
    private var gameLabel: String = "ningún juego"
    private val lookDelta = PointF()

    private var lookSens: Float = 1.0f
    private var lookInvertY: Boolean = false

    private val output = GamepadState()
    private val handler = Handler(Looper.getMainLooper())

    /** Demo: shows the pad on screen. The port replaces it with NativeInputSink. */
    private val sink = InputSink { s -> showHud(s) }

    private val frame = object : Runnable {
        override fun run() {
            composeFrame()
            handler.postDelayed(this, FRAME_MS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("x360touch", Context.MODE_PRIVATE)

        gyro = GyroAim(this, prefs)
        gameFile = GameFile(contentResolver, prefs)
        lookSens = prefs.getFloat(K_LOOK_SENS, 1.0f)
        lookInvertY = prefs.getBoolean(K_LOOK_INV_Y, false)
        cheats = CheatManager(this, prefs)

        overlay = OverlayView(this).apply {
            elements = ControlLayout.fromJson(prefs.getString(K_LAYOUT, null)) ?: ControlLayout.default()
            onGyroToggle = { gyro.toggledOn = !gyro.toggledOn }
            onSelectionChanged = { e -> syncOpacityBar(e) }
            lookEnabled = prefs.getBoolean(K_LOOK_ON, true)
            fireDragLook = prefs.getBoolean(K_FIRE_LOOK, true)
        }
        gyro.isAiming = { overlay.state.lt > 0.5f }
        gyro.rotation = { currentRotation() }

        setContentView(buildUi())
        gameFile.savedUri?.let { loadGameInfo(it) }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        gyro.refresh()
        handler.post(frame)
    }

    override fun onPause() {
        handler.removeCallbacks(frame)
        gyro.stop()
        saveLayout()
        super.onPause()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    // ---------- per-frame ----------

    private fun composeFrame() {
        output.copyFrom(overlay.state)

        // Swipe-to-look: finger speed (dp per frame) becomes right-stick deflection.
        overlay.consumeLook(lookDelta)
        if (lookDelta.x != 0f || lookDelta.y != 0f) {
            val d = resources.displayMetrics.density
            val k = 0.08f * lookSens
            var y = -(lookDelta.y / d) * k
            if (lookInvertY) y = -y
            output.rx = (output.rx + (lookDelta.x / d) * k).coerceIn(-1f, 1f)
            output.ry = (output.ry + y).coerceIn(-1f, 1f)
        }

        val active = gyro.isActive()
        if (active) {
            output.rx = (output.rx + gyro.outX).coerceIn(-1f, 1f)
            output.ry = (output.ry + gyro.outY).coerceIn(-1f, 1f)
        }
        overlay.gyroActive = active
        cheats.applyFrame()
        sink.onGamepad(output)
    }

    private fun showHud(s: GamepadState) {
        hud.text = String.format(
            "Juego: %s\nBotones 0x%04X   LS %+.2f %+.2f   RS %+.2f %+.2f   LT %.0f%%  RT %.0f%%   Giro %s",
            gameLabel,
            s.buttons, s.lx, s.ly, s.rx, s.ry, s.lt * 100, s.rt * 100,
            if (!gyro.available) "no disponible" else if (gyro.isActive()) "ON" else "off"
        )
    }

    // ---------- UI ----------

    private fun buildUi(): View {
        val root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(18, 22, 28)) }
        root.addView(overlay, FrameLayout.LayoutParams(MATCH, MATCH))

        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        editButton = smallButton("Editar") { toggleEdit() }
        bar.addView(smallButton("Juego") { pickGame() })
        bar.addView(editButton)
        bar.addView(smallButton("Cámara") { showLookDialog() })
        bar.addView(smallButton("Giroscopio") { showGyroDialog() })
        bar.addView(smallButton("Cheats") { showCheatsDialog() })
        bar.addView(smallButton("Distribución") { confirmReset() })
        root.addView(bar, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(36)
        })

        opacityBar = SeekBar(this).apply {
            max = 95
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val v = (p + 5) / 100f
                    val sel = this@MainActivity.overlay.selected
                    if (sel != null) sel.opacity = v else this@MainActivity.overlay.elements.forEach { it.opacity = v }
                    this@MainActivity.overlay.invalidate()
                }
                override fun onStartTrackingTouch(sb: SeekBar) = Unit
                override fun onStopTrackingTouch(sb: SeekBar) = Unit
            })
        }
        opacityRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setBackgroundColor(Color.argb(170, 0, 0, 0))
            setPadding(dp(12), dp(4), dp(12), dp(4))
            addView(TextView(context).apply { text = "Opacidad"; setTextColor(Color.WHITE) })
            addView(opacityBar, LinearLayout.LayoutParams(dp(220), WRAP))
            addView(smallButton("Ocultar/Mostrar") { toggleSelectedVisibility() })
        }
        root.addView(opacityRow, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply {
            topMargin = dp(80)
        })

        hud = TextView(this).apply {
            setTextColor(Color.argb(200, 255, 255, 255))
            textSize = 11f
        }
        root.addView(hud, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply {
            leftMargin = dp(12); topMargin = dp(8)
        })
        return root
    }

    private fun smallButton(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 12f
        alpha = 0.85f
        setOnClickListener { onClick() }
    }

    private fun toggleEdit() {
        overlay.editMode = !overlay.editMode
        editButton.text = if (overlay.editMode) "Listo" else "Editar"
        opacityRow.visibility = if (overlay.editMode) View.VISIBLE else View.GONE
        syncOpacityBar(null)
        if (!overlay.editMode) saveLayout()
    }

    private fun syncOpacityBar(e: ControlElement?) {
        if (!::opacityBar.isInitialized) return
        val v = e?.opacity ?: overlay.elements.firstOrNull()?.opacity ?: 0.55f
        opacityBar.progress = ((v * 100).toInt() - 5).coerceIn(0, 95)
    }

    /** Hidden controls stay faintly visible in edit mode so they can be shown again. */
    private fun toggleSelectedVisibility() {
        val e = overlay.selected ?: return
        e.visible = !e.visible
        overlay.invalidate()
    }

    private fun confirmReset() {
        AlertDialog.Builder(this)
            .setTitle("Distribución de botones")
            .setMessage("Elige una distribución. Reemplaza la posición y el tamaño actuales de los botones.")
            .setPositiveButton("Shooter (tipo PUBG)") { _, _ ->
                overlay.elements = ControlLayout.shooter()
                saveLayout()
            }
            .setNeutralButton("Mando Xbox") { _, _ ->
                overlay.elements = ControlLayout.xboxPad()
                saveLayout()
            }
            .setNegativeButton("Cancelar", null)
            .setOnDismissListener { hideSystemBars() }
            .show()
    }

    private fun showGyroDialog() {
        if (!gyro.available) {
            AlertDialog.Builder(this)
                .setTitle("Giroscopio")
                .setMessage("Este dispositivo no tiene giroscopio.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        val pad = dp(20)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        val group = RadioGroup(this)
        GyroAim.Mode.values().forEachIndexed { i, m ->
            group.addView(RadioButton(this).apply {
                id = View.generateViewId()
                text = m.label
                tag = m
                isChecked = gyro.mode == m
            })
        }
        group.setOnCheckedChangeListener { g, checkedId ->
            val m = g.findViewById<RadioButton>(checkedId)?.tag as? GyroAim.Mode ?: return@setOnCheckedChangeListener
            gyro.mode = m
            gyro.refresh()
        }
        box.addView(group)

        val sensLabel = TextView(this)
        fun updateLabel() { sensLabel.text = String.format("Sensibilidad: %.1f", gyro.sensitivity) }
        updateLabel()
        box.addView(sensLabel)
        box.addView(SeekBar(this).apply {
            max = 39
            progress = ((gyro.sensitivity * 10).toInt() - 1).coerceIn(0, 39)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    gyro.sensitivity = (p + 1) / 10f
                    updateLabel()
                }
                override fun onStartTrackingTouch(sb: SeekBar) = Unit
                override fun onStopTrackingTouch(sb: SeekBar) = Unit
            })
        })
        box.addView(CheckBox(this).apply {
            text = "Invertir horizontal"
            isChecked = gyro.invertX
            setOnCheckedChangeListener { _, c -> gyro.invertX = c }
        })
        box.addView(CheckBox(this).apply {
            text = "Invertir vertical"
            isChecked = gyro.invertY
            setOnCheckedChangeListener { _, c -> gyro.invertY = c }
        })

        AlertDialog.Builder(this)
            .setTitle("Apuntar con giroscopio")
            .setView(box)
            .setPositiveButton("Listo", null)
            .setOnDismissListener { hideSystemBars() }
            .show()
    }

    private fun showCheatsDialog() {
        val list = cheats.cheats
        val b = AlertDialog.Builder(this).setTitle("Cheats")
        if (list.isEmpty()) {
            b.setMessage("No hay cheats definidos todavía.\n\nSe agregan en app/src/main/assets/cheats.json (formato en el README).")
        } else {
            b.setMultiChoiceItems(
                list.map { it.name }.toTypedArray(),
                list.map { it.enabled }.toBooleanArray()
            ) { _, which, on -> cheats.setEnabled(list[which].id, on) }
        }
        b.setPositiveButton("Cerrar", null)
            .setOnDismissListener { hideSystemBars() }
            .show()
    }

    // ---------- game file ----------

    private fun pickGame() {
        AlertDialog.Builder(this)
            .setTitle("Cargar juego")
            .setMessage("Actual: $gameLabel")
            .setPositiveButton("Archivo ISO") { _, _ -> pickIso() }
            .setNeutralButton("Carpeta (extraído)") { _, _ -> pickFolder() }
            .setNegativeButton("Cancelar", null)
            .setOnDismissListener { hideSystemBars() }
            .show()
    }

    private fun pickIso() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        runCatching { startActivityForResult(i, REQ_GAME) }
    }

    private fun pickFolder() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        }
        runCatching { startActivityForResult(i, REQ_GAME) }
    }

    @Deprecated("Activity API")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_GAME || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        gameFile.remember(uri)
        loadGameInfo(uri, showResult = true)
    }

    private fun loadGameInfo(uri: android.net.Uri, showResult: Boolean = false) {
        gameLabel = "comprobando…"
        thread(name = "game-inspect") {
            val info = gameFile.inspect(uri)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (info == null) {
                    gameLabel = "no se pudo leer el archivo"
                    gameFile.forget()
                } else {
                    val gb = if (info.sizeBytes > 0) String.format(" (%.1f GB)", info.sizeBytes / 1e9) else ""
                    gameLabel = info.name + gb + when {
                        info.validXbox360 -> " ✓"
                        info.isFolder -> " ✗ no tiene default.xex"
                        else -> " ✗ no es ISO de Xbox 360"
                    }
                }
                if (showResult) showGameDialog(info)
            }
        }
    }

    private fun showGameDialog(info: GameFile.Info?) {
        val msg = when {
            info == null -> "No se pudo leer el archivo. Vuelve a elegirlo."
            info.validXbox360 && info.isFolder -> "${info.name}\n\nCarpeta de juego válida (contiene default.xex). Ruta guardada.\n\n" +
                "Nota: esta app es la capa de controles; el juego se ejecutará cuando el port del motor esté listo."
            info.isFolder -> "${info.name}\n\nEsta carpeta no tiene default.xex. Elige la carpeta raíz del juego extraído."
            info.validXbox360 -> "${info.name}\n\nISO de Xbox 360 válido. Ruta guardada: se recordará al reabrir la app.\n\n" +
                "Nota: esta app es la capa de controles; el juego se ejecutará cuando el port del motor esté listo."
            else -> "${info.name}\n\nNo tiene la firma de un disco de Xbox 360. Elige el .iso del juego."
        }
        AlertDialog.Builder(this)
            .setTitle("Juego")
            .setMessage(msg)
            .setPositiveButton("OK", null)
            .setOnDismissListener { hideSystemBars() }
            .show()
    }

    // ---------- swipe camera ----------

    private fun showLookDialog() {
        val pad = dp(20)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad / 2, pad, 0)
        }
        box.addView(TextView(this).apply {
            text = "Desliza el dedo en la parte derecha de la pantalla (fuera de los botones) para mover la cámara, como en PUBG."
        })
        box.addView(CheckBox(this).apply {
            text = "Cámara con deslizamiento"
            isChecked = this@MainActivity.overlay.lookEnabled
            setOnCheckedChangeListener { _, c ->
                this@MainActivity.overlay.lookEnabled = c
                prefs.edit().putBoolean(K_LOOK_ON, c).apply()
            }
        })
        box.addView(CheckBox(this).apply {
            text = "Mover la cámara arrastrando el botón de disparo (RT)"
            isChecked = this@MainActivity.overlay.fireDragLook
            setOnCheckedChangeListener { _, c ->
                this@MainActivity.overlay.fireDragLook = c
                prefs.edit().putBoolean(K_FIRE_LOOK, c).apply()
            }
        })
        val label = TextView(this)
        fun update() { label.text = String.format("Sensibilidad: %.1f", lookSens) }
        update()
        box.addView(label)
        box.addView(SeekBar(this).apply {
            max = 39
            progress = ((lookSens * 10).toInt() - 1).coerceIn(0, 39)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                    lookSens = (p + 1) / 10f
                    prefs.edit().putFloat(K_LOOK_SENS, lookSens).apply()
                    update()
                }
                override fun onStartTrackingTouch(sb: SeekBar) = Unit
                override fun onStopTrackingTouch(sb: SeekBar) = Unit
            })
        })
        box.addView(CheckBox(this).apply {
            text = "Invertir vertical"
            isChecked = lookInvertY
            setOnCheckedChangeListener { _, c ->
                lookInvertY = c
                prefs.edit().putBoolean(K_LOOK_INV_Y, c).apply()
            }
        })
        AlertDialog.Builder(this)
            .setTitle("Cámara táctil")
            .setView(box)
            .setPositiveButton("Listo", null)
            .setOnDismissListener { hideSystemBars() }
            .show()
    }

    // ---------- helpers ----------

    private fun saveLayout() {
        prefs.edit().putString(K_LAYOUT, ControlLayout.toJson(overlay.elements)).apply()
    }

    @Suppress("DEPRECATION")
    private fun currentRotation(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) display?.rotation ?: 1
        else windowManager.defaultDisplay.rotation

    @Suppress("DEPRECATION")
    private fun hideSystemBars() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    companion object {
        private const val K_LAYOUT = "layout_v2"
        private const val K_LOOK_ON = "look_on"
        private const val K_FIRE_LOOK = "look_fire_drag"
        private const val K_LOOK_SENS = "look_sens"
        private const val K_LOOK_INV_Y = "look_inv_y"
        private const val REQ_GAME = 1001
        private const val FRAME_MS = 16L
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}
