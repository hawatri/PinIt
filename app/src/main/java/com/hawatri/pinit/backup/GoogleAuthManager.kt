package com.hawatri.pinit.backup

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.services.drive.DriveScopes

/**
 * Wraps [GoogleSignInClient] with the DRIVE_FILE scope. The DRIVE_FILE scope only
 * grants the app access to files it created — backups appear in the user's "My Drive"
 * under PinIt/, the user can see and delete them, and the app cannot touch any
 * other Drive content.
 *
 * Threading: callers should perform sign-in via [signInIntent] on the main thread
 * and pass the result into [onSignInResult]; everything else is safe from any thread.
 */
object GoogleAuthManager {

    private fun signInOptions(): GoogleSignInOptions =
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestProfile()
            .requestScopes(Scope(DriveScopes.DRIVE_FILE))
            .build()

    fun client(context: Context): GoogleSignInClient =
        GoogleSignIn.getClient(context.applicationContext, signInOptions())

    /** Intent to feed into an ActivityResultLauncher. */
    fun signInIntent(context: Context): Intent = client(context).signInIntent

    /** Last cached account, or null if the user is not signed in or has revoked access. */
    fun currentAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context.applicationContext)?.takeIf {
            GoogleSignIn.hasPermissions(it, Scope(DriveScopes.DRIVE_FILE))
        }

    /**
     * Build a [GoogleAccountCredential] tied to [account] for the Drive REST client.
     * Never returns null — caller has already verified the account exists.
     */
    fun credentialFor(context: Context, account: GoogleSignInAccount): GoogleAccountCredential {
        val cred = GoogleAccountCredential.usingOAuth2(
            context.applicationContext,
            listOf(DriveScopes.DRIVE_FILE)
        )
        cred.selectedAccount = account.account
        return cred
    }

    /** Sign out and revoke the cached token. Suspends until both finish. */
    suspend fun signOut(context: Context) {
        val c = client(context)
        runCatching { com.google.android.gms.tasks.Tasks.await(c.signOut()) }
        runCatching { com.google.android.gms.tasks.Tasks.await(c.revokeAccess()) }
    }
}
