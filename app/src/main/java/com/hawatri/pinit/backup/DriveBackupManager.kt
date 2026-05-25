package com.hawatri.pinit.backup

import android.content.Context
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.model.File as DriveFile
import java.io.ByteArrayOutputStream

/**
 * Thin wrapper around the Drive v3 REST client. Owns the PinIt/ folder lifecycle
 * (creates it on first use), and uploads / downloads the single
 * [PinItBackup.FILE_NAME] file inside it.
 *
 * All methods are blocking — call from a worker thread. Callers in this codebase
 * use Dispatchers.IO via [BackupSyncManager].
 */
class DriveBackupManager(
    context: Context,
    credential: GoogleAccountCredential
) {
    private val drive: Drive = Drive.Builder(
        NetHttpTransport(),
        GsonFactory.getDefaultInstance(),
        credential
    )
        .setApplicationName("PinIt")
        .build()

    /** Returns the folder id, creating it if it doesn't exist. */
    fun findOrCreatePinItFolder(): String {
        // Look for an existing folder with our name in the user's My Drive root.
        val query = "mimeType = 'application/vnd.google-apps.folder' " +
                "and name = '${PinItBackup.DRIVE_FOLDER_NAME}' " +
                "and trashed = false"
        val existing = drive.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name)")
            .execute()
            .files
        if (!existing.isNullOrEmpty()) return existing[0].id

        // Create it under root. With the DRIVE_FILE scope, we can create files in
        // the user's Drive — the user sees them, but we can only access ones we
        // created.
        val meta = DriveFile().apply {
            name = PinItBackup.DRIVE_FOLDER_NAME
            mimeType = "application/vnd.google-apps.folder"
        }
        return drive.files().create(meta).setFields("id").execute().id
    }

    /** File id of the existing backup inside [folderId], or null if none. */
    fun findBackupFile(folderId: String): String? {
        val query = "name = '${PinItBackup.FILE_NAME}' " +
                "and '${folderId}' in parents " +
                "and trashed = false"
        val files = drive.files().list()
            .setQ(query)
            .setSpaces("drive")
            .setFields("files(id, name, modifiedTime)")
            .execute()
            .files
        return files?.firstOrNull()?.id
    }

    /** Upload [json] as the backup file inside the PinIt folder, replacing if it exists. */
    fun uploadBackup(folderId: String, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val content = ByteArrayContent(PinItBackup.MIME_TYPE, bytes)

        val existing = findBackupFile(folderId)
        if (existing == null) {
            val meta = DriveFile().apply {
                name = PinItBackup.FILE_NAME
                parents = listOf(folderId)
            }
            drive.files().create(meta, content).setFields("id").execute()
        } else {
            // Update content; can't change parents in this call without addParents/removeParents.
            val meta = DriveFile().apply { name = PinItBackup.FILE_NAME }
            drive.files().update(existing, meta, content).execute()
        }
    }

    /** Returns the JSON contents of the backup file, or null if no backup exists yet. */
    fun downloadBackupOrNull(folderId: String): String? {
        val id = findBackupFile(folderId) ?: return null
        val out = ByteArrayOutputStream()
        drive.files().get(id).executeMediaAndDownloadTo(out)
        return out.toString(Charsets.UTF_8.name())
    }
}
