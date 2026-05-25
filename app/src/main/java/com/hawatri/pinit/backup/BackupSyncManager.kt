package com.hawatri.pinit.backup

import android.content.Context
import com.hawatri.pinit.data.AppPreferences
import com.hawatri.pinit.data.Note
import com.hawatri.pinit.data.NoteDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Orchestrates Google Drive backup / restore for PinIt.
 *
 * - **Manual back up** ([backupNow]): snapshot every local note, base64-encode owned
 *   audio files, upload as `My Drive/PinIt/pinit_backup.pinit`.
 * - **Manual restore** ([restoreNow]): download the archive, write audio blobs back
 *   to `filesDir/recordings/`, merge into the local DB by `note.id`. The merge picks
 *   the side with the newer `timestamp` per id, and unions ids that exist on only
 *   one side. After a successful restore we also re-upload the merged result so
 *   cloud and device match.
 * - **Auto-merge on first sign-in** ([signInAndSync]): runs merge + re-upload exactly
 *   once per (account, device) pair so the user lands with a unified note set.
 *
 * Conflict policy: last-write-wins by timestamp. Caveat: a note deleted on this
 * device before the merge will be resurrected from cloud — proper deletion-aware
 * sync would need a tombstone table, deferred for v1.
 */
object BackupSyncManager {

    sealed class State {
        data object Idle : State()
        data class Working(val message: String) : State()
        data class Success(val message: String) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state

    /**
     * One-shot merge run on first sign-in for a given account on this device. Safe
     * to call repeatedly — does nothing after the initial merge has succeeded.
     */
    suspend fun signInAndSync(context: Context) {
        if (AppPreferences.isInitialMergeDone(context)) return
        runMerge(context, source = "Sign-in")
        AppPreferences.setInitialMergeDone(context, done = true)
    }

    /** Manual "Back up now" — uploads the current local state, no merge. */
    suspend fun backupNow(context: Context) {
        _state.value = State.Working("Backing up to Drive…")
        try {
            val account = GoogleAuthManager.currentAccount(context)
                ?: throw IllegalStateException("Not signed in")
            val drive = DriveBackupManager(context, GoogleAuthManager.credentialFor(context, account))

            val (folderId, json) = withContext(Dispatchers.IO) {
                val notes = snapshotNotes(context)
                val blobs = PinItBackup.collectAudioBlobs(context, notes)
                val backup = PinItBackup(
                    exportedAt = System.currentTimeMillis(),
                    device = android.os.Build.MODEL ?: "Android",
                    notes = notes,
                    audioBlobs = blobs
                )
                val folderId = drive.findOrCreatePinItFolder()
                val json = PinItBackup.toJson(backup)
                drive.uploadBackup(folderId, json)
                folderId to json
            }
            AppPreferences.setLastSyncAt(context, System.currentTimeMillis())
            _state.value = State.Success("Backed up ${json.length / 1024} KB to Drive")
        } catch (e: Exception) {
            _state.value = State.Error(e.message ?: "Backup failed")
        }
    }

    /** Manual "Restore" — runs the same merge logic as auto-sync, surfaces UI feedback. */
    suspend fun restoreNow(context: Context) {
        runMerge(context, source = "Restore")
    }

    private suspend fun runMerge(context: Context, source: String) {
        _state.value = State.Working("$source — checking Drive…")
        try {
            val account = GoogleAuthManager.currentAccount(context)
                ?: throw IllegalStateException("Not signed in")
            val drive = DriveBackupManager(context, GoogleAuthManager.credentialFor(context, account))

            val result = withContext(Dispatchers.IO) {
                val folderId = drive.findOrCreatePinItFolder()
                val cloudJson = drive.downloadBackupOrNull(folderId)

                if (cloudJson == null) {
                    // No cloud backup yet — push local up so subsequent sign-ins find it.
                    val notes = snapshotNotes(context)
                    val blobs = PinItBackup.collectAudioBlobs(context, notes)
                    val backup = PinItBackup(
                        exportedAt = System.currentTimeMillis(),
                        device = android.os.Build.MODEL ?: "Android",
                        notes = notes,
                        audioBlobs = blobs
                    )
                    drive.uploadBackup(folderId, PinItBackup.toJson(backup))
                    return@withContext MergeOutcome(uploaded = notes.size, restored = 0, finalCount = notes.size)
                }

                val cloud = PinItBackup.fromJson(cloudJson)
                // Materialise audio blobs first so the path remap is ready.
                val pathRemap = PinItBackup.writeAudioBlobs(context, cloud.audioBlobs)
                val cloudNotes = PinItBackup.rewriteAudioPaths(cloud.notes, pathRemap)
                val localNotes = snapshotNotes(context)

                val merged = mergeByTimestamp(localNotes, cloudNotes)

                // Persist merged set: insertNote uses REPLACE so this works as upsert.
                val dao = NoteDatabase.getDatabase(context).noteDao()
                merged.forEach { dao.insertNote(it) }

                // Push merged state back so cloud reflects the union.
                val mergedBlobs = PinItBackup.collectAudioBlobs(context, merged)
                val mergedBackup = PinItBackup(
                    exportedAt = System.currentTimeMillis(),
                    device = android.os.Build.MODEL ?: "Android",
                    notes = merged,
                    audioBlobs = mergedBlobs
                )
                drive.uploadBackup(folderId, PinItBackup.toJson(mergedBackup))

                MergeOutcome(
                    uploaded = merged.size,
                    restored = cloudNotes.count { c -> localNotes.none { it.id == c.id } },
                    finalCount = merged.size
                )
            }

            AppPreferences.setLastSyncAt(context, System.currentTimeMillis())
            _state.value = State.Success(
                if (result.restored > 0) "Restored ${result.restored} notes; ${result.finalCount} total"
                else "Up to date — ${result.finalCount} notes"
            )
        } catch (e: Exception) {
            _state.value = State.Error(e.message ?: "$source failed")
        }
    }

    /**
     * Union by id; if both sides have the same id, keep whichever has the newer
     * `timestamp`. Stable so the order of returned notes mirrors what the DAO
     * already returns.
     */
    private fun mergeByTimestamp(local: List<Note>, cloud: List<Note>): List<Note> {
        val byId = HashMap<String, Note>(local.size + cloud.size)
        local.forEach { byId[it.id] = it }
        cloud.forEach { c ->
            val existing = byId[c.id]
            if (existing == null || c.timestamp > existing.timestamp) byId[c.id] = c
        }
        return byId.values.sortedByDescending { it.timestamp }
    }

    private suspend fun snapshotNotes(context: Context): List<Note> =
        NoteDatabase.getDatabase(context).noteDao().getAllNotes().first()

    private data class MergeOutcome(val uploaded: Int, val restored: Int, val finalCount: Int)

    fun reset() { _state.value = State.Idle }
}
