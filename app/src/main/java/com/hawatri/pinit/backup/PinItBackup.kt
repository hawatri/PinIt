package com.hawatri.pinit.backup

import android.content.Context
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.hawatri.pinit.data.Note
import com.hawatri.pinit.ui.AudioNoteData
import com.hawatri.pinit.data.NoteType
import java.io.File

/**
 * On-disk format of a `.pinit` backup. Versioned so we can evolve the schema later
 * while still reading old archives. Audio recordings live in `filesDir/recordings/`
 * and we own their bytes — those get base64-encoded into [audioBlobs] keyed by
 * filename. Image / PDF notes hold `content://` URIs we don't own, so we don't try
 * to read them; on restore the URI is rebound and may or may not still resolve on
 * the restoring device.
 */
data class PinItBackup(
    val schemaVersion: Int = SCHEMA_VERSION,
    val exportedAt: Long,
    val device: String,
    val notes: List<Note>,
    /** filename inside filesDir/recordings/ → base64-encoded bytes. */
    val audioBlobs: Map<String, String>
) {
    companion object {
        const val SCHEMA_VERSION = 1
        const val FILE_NAME = "pinit_backup.pinit"
        const val MIME_TYPE = "application/octet-stream"
        const val DRIVE_FOLDER_NAME = "PinIt"

        private val gson: Gson = GsonBuilder()
            .serializeNulls()
            .create()

        fun toJson(backup: PinItBackup): String = gson.toJson(backup)
        fun fromJson(json: String): PinItBackup = gson.fromJson(json, PinItBackup::class.java)

        /**
         * Walk every AUDIO note, locate the recording on disk, base64 the bytes,
         * and return them keyed by filename. Missing files are skipped silently —
         * the note still restores, audio just won't play.
         */
        fun collectAudioBlobs(context: Context, notes: List<Note>): Map<String, String> {
            val out = mutableMapOf<String, String>()
            val recordingsDir = File(context.filesDir, "recordings")
            notes.filter { it.noteType == NoteType.AUDIO }.forEach { note ->
                val data = try { gson.fromJson(note.text, AudioNoteData::class.java) } catch (e: Exception) { null }
                val path = data?.path ?: return@forEach
                val file = File(path)
                // Only include files that live in our own recordings dir; never base64
                // arbitrary user files referenced by an external path.
                if (!file.exists() || !file.absolutePath.startsWith(recordingsDir.absolutePath)) return@forEach
                runCatching {
                    val bytes = file.readBytes()
                    out[file.name] = Base64.encodeToString(bytes, Base64.NO_WRAP)
                }
            }
            return out
        }

        /**
         * Decode every blob back into filesDir/recordings/, overwriting if it already
         * exists. Returns a remap of `oldPath → newPath` so the caller can rewrite
         * each AudioNoteData.path before persisting the restored notes — the absolute
         * path almost always differs across devices (different uid, different filesDir).
         */
        fun writeAudioBlobs(context: Context, blobs: Map<String, String>): Map<String, String> {
            val recordingsDir = File(context.filesDir, "recordings").apply { mkdirs() }
            val pathRemap = mutableMapOf<String, String>()
            blobs.forEach { (filename, b64) ->
                runCatching {
                    val bytes = Base64.decode(b64, Base64.NO_WRAP)
                    val file = File(recordingsDir, filename)
                    file.writeBytes(bytes)
                    pathRemap[filename] = file.absolutePath
                }
            }
            return pathRemap
        }

        /**
         * Apply [pathRemap] (as returned by [writeAudioBlobs]) to a list of notes,
         * rewriting AudioNoteData.path to point to the local copy.
         */
        fun rewriteAudioPaths(notes: List<Note>, pathRemap: Map<String, String>): List<Note> {
            if (pathRemap.isEmpty()) return notes
            return notes.map { note ->
                if (note.noteType != NoteType.AUDIO) return@map note
                val data = try { gson.fromJson(note.text, AudioNoteData::class.java) } catch (e: Exception) { null }
                    ?: return@map note
                val originalName = File(data.path).name
                val newPath = pathRemap[originalName] ?: return@map note
                val updated = data.copy(path = newPath)
                note.copy(text = gson.toJson(updated))
            }
        }
    }
}
