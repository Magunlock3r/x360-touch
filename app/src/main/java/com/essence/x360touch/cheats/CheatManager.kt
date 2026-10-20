package com.essence.x360touch.cheats

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import org.json.JSONArray

/** One memory write. Address is the Xbox 360 guest address (e.g. 0x82A00000). */
data class CheatWrite(val address: Long, val value: Long, val size: Int)

data class Cheat(
    val id: String,
    val name: String,
    val description: String,
    val writes: List<CheatWrite>,
    var enabled: Boolean = false
)

/** Writes into the game's memory. The port provides the real implementation. */
fun interface MemoryWriter {
    fun write(address: Long, value: Long, size: Int): Boolean
}

/** Used when no game is running (this demo): it only logs. */
object LoggingMemoryWriter : MemoryWriter {
    override fun write(address: Long, value: Long, size: Int): Boolean {
        Log.d("Cheats", "write 0x%08X = 0x%X (%d bytes)".format(address, value, size))
        return false
    }
}

/**
 * Loads cheats from assets/cheats.json and re-applies the enabled ones every frame
 * (frozen values, like Action Replay codes).
 *
 * Format:
 * [{"id":"inf_ammo","name":"Munición infinita","description":"...",
 *   "writes":[{"address":"0x82000000","value":"0x63","size":4}]}]
 */
class CheatManager(
    context: Context,
    private val prefs: SharedPreferences,
    var writer: MemoryWriter = LoggingMemoryWriter
) {
    val cheats: List<Cheat> = load(context)

    init {
        val enabled = prefs.getStringSet(K_ENABLED, emptySet()) ?: emptySet()
        cheats.forEach { it.enabled = it.id in enabled }
    }

    fun setEnabled(id: String, on: Boolean) {
        cheats.firstOrNull { it.id == id }?.enabled = on
        prefs.edit()
            .putStringSet(K_ENABLED, cheats.filter { it.enabled }.map { it.id }.toSet())
            .apply()
    }

    fun applyFrame() {
        for (c in cheats) {
            if (!c.enabled) continue
            for (w in c.writes) writer.write(w.address, w.value, w.size)
        }
    }

    private fun load(context: Context): List<Cheat> = try {
        val json = context.assets.open(ASSET).bufferedReader().use { it.readText() }
        val arr = JSONArray(json)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val ws = o.getJSONArray("writes")
            Cheat(
                id = o.getString("id"),
                name = o.getString("name"),
                description = o.optString("description", ""),
                writes = (0 until ws.length()).map { j ->
                    val w = ws.getJSONObject(j)
                    val size = w.optInt("size", 4)
                    require(size == 1 || size == 2 || size == 4) { "size must be 1, 2 or 4" }
                    CheatWrite(
                        address = java.lang.Long.decode(w.getString("address")),
                        value = java.lang.Long.decode(w.getString("value")),
                        size = size
                    )
                }
            )
        }
    } catch (t: Throwable) {
        Log.e("Cheats", "cheats.json inválido", t)
        emptyList()
    }

    companion object {
        private const val ASSET = "cheats.json"
        private const val K_ENABLED = "cheats_enabled"
    }
}
