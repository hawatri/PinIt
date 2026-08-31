package com.hawatri.pinit.backup

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import com.hawatri.pinit.R
import com.hawatri.pinit.data.AppPreferences
import com.hawatri.pinit.data.Note
import com.hawatri.pinit.data.NoteDatabase
import com.hawatri.pinit.util.LocaleHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

    private const val TAG = "PinItBackup"

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
        runMerge(context, sourceRes = R.string.backup_source_signin)
        AppPreferences.setInitialMergeDone(context, done = true)
    }

    /** Manual "Back up now" — uploads the current local state, no merge. */
    suspend fun backupNow(context: Context) {
        _state.value = State.Working(LocaleHelper.getString(context, R.string.backup_status_uploading))
        try {
            val account = GoogleAuthManager.currentAccount(context)
                ?: throw IllegalStateException(LocaleHelper.getString(context, R.string.backup_err_not_signed_in))
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
            AppPreferences.setLastBackupNow(context)
            _state.value = State.Success(
                LocaleHelper.getString(context, R.string.backup_status_uploaded, json.length / 1024)
            )
        } catch (e: Exception) {
            Log.e(TAG, "backupNow failed", e)
            _state.value = State.Error(
                e.message ?: LocaleHelper.getString(context, R.string.backup_err_failed)
            )
        }
    }

    /**
     * "Take offline backup" — writes a `pinit_backup_<yyyyMMdd_HHmmss>.pinit` file into
     * `Download/PinIt/` via MediaStore. No permissions required on Android 10+ since
     * MediaStore.Downloads is the public, scoped-storage path. Visible to the user
     * via Files / file managers / USB.
     */
    suspend fun backupOfflineNow(context: Context) {
        _state.value = State.Working(LocaleHelper.getString(context, R.string.backup_status_saving_offline))
        try {
            val savedPath = withContext(Dispatchers.IO) {
                val notes = snapshotNotes(context)
                val blobs = PinItBackup.collectAudioBlobs(context, notes)
                val backup = PinItBackup(
                    exportedAt = System.currentTimeMillis(),
                    device = android.os.Build.MODEL ?: "Android",
                    notes = notes,
                    audioBlobs = blobs
                )
                val json = PinItBackup.toJson(backup)
                writeToDownloads(context, json)
            }
            AppPreferences.setLastBackupNow(context)
            _state.value = State.Success(
                LocaleHelper.getString(context, R.string.backup_status_saved_to, savedPath)
            )
        } catch (e: Exception) {
            Log.e(TAG, "backupOfflineNow failed", e)
            _state.value = State.Error(
                e.message ?: LocaleHelper.getString(context, R.string.backup_err_offline_failed)
            )
        }
    }

    private fun writeToDownloads(context: Context, json: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val filename = "pinit_backup_$timestamp.pinit"
        val relativePath = Environment.DIRECTORY_DOWNLOADS + "/PinIt"

        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, PinItBackup.MIME_TYPE)
            put(MediaStore.Downloads.RELATIVE_PATH, relativePath)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException(LocaleHelper.getString(context, R.string.backup_err_create_download))

        try {
            resolver.openOutputStream(uri)?.use { it.write(json.toByteArray(Charsets.UTF_8)) }
                ?: throw IllegalStateException(LocaleHelper.getString(context, R.string.backup_err_open_download))
            // Mark visible to the system after the write completes.
            val finalize = ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }
            resolver.update(uri, finalize, null, null)
        } catch (e: Exception) {
            // Roll back the placeholder so we don't leave an empty file behind.
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return "Download/PinIt/$filename"
    }

    /** Manual "Restore" — runs the same merge logic as auto-sync, surfaces UI feedback. */
    suspend fun restoreNow(context: Context) {
        runMerge(context, sourceRes = R.string.backup_source_restore)
    }

    /**
     * Restore from a `.pinit` file the user picked from local storage. Uses the
     * same merge-by-timestamp logic as Drive restore so a partial local archive
     * can be combined with whatever's already on the device. No Drive round-trip.
     */
    suspend fun restoreFromUri(context: Context, uri: android.net.Uri) {
        _state.value = State.Working(LocaleHelper.getString(context, R.string.backup_status_restoring_file))
        try {
            val result = withContext(Dispatchers.IO) {
                val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                    stream.bufferedReader(Charsets.UTF_8).readText()
                } ?: throw IllegalStateException(LocaleHelper.getString(context, R.string.backup_err_read_file))

                val archive = PinItBackup.fromJson(json)
                val pathRemap = PinItBackup.writeAudioBlobs(context, archive.audioBlobs)
                val archiveNotes = PinItBackup.rewriteAudioPaths(archive.notes, pathRemap)
                val localNotes = snapshotNotes(context)

                val merged = mergeByTimestamp(localNotes, archiveNotes)
                val dao = NoteDatabase.getDatabase(context).noteDao()
                merged.forEach { dao.insertNote(it) }

                MergeOutcome(
                    uploaded = merged.size,
                    restored = archiveNotes.count { a -> localNotes.none { it.id == a.id } },
                    finalCount = merged.size
                )
            }
            AppPreferences.setLastBackupNow(context)
            _state.value = State.Success(
                if (result.restored > 0) {
                    LocaleHelper.getString(
                        context, R.string.backup_status_restored,
                        result.restored, result.finalCount
                    )
                } else {
                    LocaleHelper.getString(
                        context, R.string.backup_status_up_to_date, result.finalCount
                    )
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "restoreFromUri failed", e)
            _state.value = State.Error(
                e.message ?: LocaleHelper.getString(context, R.string.backup_err_restore_failed)
            )
        }
    }

    private suspend fun runMerge(context: Context, sourceRes: Int) {
        val source = LocaleHelper.getString(context, sourceRes)
        _state.value = State.Working(
            LocaleHelper.getString(context, R.string.backup_status_checking, source)
        )
        try {
            val account = GoogleAuthManager.currentAccount(context)
                ?: throw IllegalStateException(LocaleHelper.getString(context, R.string.backup_err_not_signed_in))
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
            AppPreferences.setLastBackupNow(context)
            _state.value = State.Success(
                if (result.restored > 0) {
                    LocaleHelper.getString(
                        context, R.string.backup_status_restored,
                        result.restored, result.finalCount
                    )
                } else {
                    LocaleHelper.getString(
                        context, R.string.backup_status_up_to_date, result.finalCount
                    )
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "$source merge failed", e)
            _state.value = State.Error(
                e.message ?: LocaleHelper.getString(context, R.string.backup_err_source_failed, source)
            )
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

    /** Surface a sign-in or other auth-layer error in the same status flow the UI watches. */
    fun setError(message: String) { _state.value = State.Error(message) }

    /** Surface a transient auth-layer progress message in the same status flow the UI watches. */
    fun setWorking(message: String) { _state.value = State.Working(message) }
}
