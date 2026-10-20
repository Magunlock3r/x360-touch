package com.essence.x360touch.game

import android.content.ContentResolver
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import java.io.FileInputStream
import java.nio.ByteBuffer

/**
 * The game disc image chosen by the user (Storage Access Framework).
 * The URI keeps its read permission across restarts.
 */
class GameFile(private val resolver: ContentResolver, private val prefs: SharedPreferences) {

    /** [isFolder]: extracted game (folder with default.xex) instead of an ISO. */
    data class Info(
        val uri: Uri,
        val name: String,
        val sizeBytes: Long,
        val validXbox360: Boolean,
        val isFolder: Boolean = false
    )

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
    fun inspect(uri: Uri): Info? =
        if (DocumentsContract.isTreeUri(uri)) inspectFolder(uri) else inspectIso(uri)

    /** An extracted Xbox 360 game is a folder with default.xex at its root. */
    private fun inspectFolder(tree: Uri): Info? = runCatching {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val rootDoc = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        var name = rootId.substringAfterLast(':').substringAfterLast('/').ifEmpty { "carpeta" }
        resolver.query(rootDoc, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0)?.let { name = it } }
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        var hasXex = false
        resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { c ->
                while (c.moveToNext()) {
                    if (c.getString(0).equals("default.xex", ignoreCase = true)) {
                        hasXex = true
                        break
                    }
                }
            }
        Info(tree, name, -1L, hasXex, isFolder = true)
    }.getOrNull()

    private fun inspectIso(uri: Uri): Info? = runCatching {
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
