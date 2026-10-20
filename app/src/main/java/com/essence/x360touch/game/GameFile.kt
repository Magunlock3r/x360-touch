package com.essence.x360touch.game

import android.content.ContentResolver
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.OpenableColumns
import java.io.FileInputStream
import java.nio.ByteBuffer

/**
 * The game disc image chosen by the user (Storage Access Framework).
 * The URI keeps its read permission across restarts.
 */
class GameFile(private val resolver: ContentResolver, private val prefs: SharedPreferences) {

    data class Info(val uri: Uri, val name: String, val sizeBytes: Long, val validXbox360: Boolean)

    val savedUri: Uri?
        get() = prefs.getString(K_URI, null)?.let(Uri::parse)

    /** Keeps read access to the file after the app restarts, and remembers it. */
    fun remember(uri: Uri) {
        runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val old = savedUri
        if (old != null && old != uri) {
            runCatching { resolver.releasePersistableUriPermission(old, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        prefs.edit().putString(K_URI, uri.toString()).apply()
    }

    fun forget() {
        savedUri?.let { u ->
            runCatching { resolver.releasePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        }
        prefs.edit().remove(K_URI).apply()
    }

    /** Reads name, size and checks the Xbox 360 disc signature. Blocking: call off the main thread. */
    fun inspect(uri: Uri): Info? = runCatching {
        var name = uri.lastPathSegment ?: "juego.iso"
        var size = -1L
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
            ?.use { c ->
                if (c.moveToFirst()) {
                    c.getString(0)?.let { name = it }
                    if (!c.isNull(1)) size = c.getLong(1)
                }
            }
        Info(uri, name, size, isXbox360Image(uri))
    }.getOrNull()

    /**
     * An Xbox 360 game ISO has the XDVDFS signature "MICROSOFT*XBOX*MEDIA" at sector 32
     * of the game partition. The partition starts at a different offset per disc type.
     */
    private fun isXbox360Image(uri: Uri): Boolean {
        val pfd = resolver.openFileDescriptor(uri, "r") ?: return false
        pfd.use {
            FileInputStream(it.fileDescriptor).use { input ->
                val ch = input.channel
                val buf = ByteBuffer.allocate(MAGIC.size)
                for (partition in PARTITIONS) {
                    val pos = partition + 0x10000L
                    if (it.statSize in 0..(pos + MAGIC.size)) continue
                    buf.clear()
                    if (ch.read(buf, pos) != MAGIC.size) continue
                    if (buf.array().contentEquals(MAGIC)) return true
                }
            }
        }
        return false
    }

    companion object {
        private const val K_URI = "game_uri"
        private val MAGIC = "MICROSOFT*XBOX*MEDIA".toByteArray(Charsets.US_ASCII)
        // Extracted image / XGD3 / XGD2 / XGD1 (Gears of War 1 is XGD2).
        private val PARTITIONS = longArrayOf(0L, 0x2080000L, 0xFD90000L, 0x18300000L)
    }
}
