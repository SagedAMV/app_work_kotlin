package com.majarra.galaxy.security

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.majarra.galaxy.domain.repository.UriPermissionVault
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UriPermissionVaultImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : UriPermissionVault {

    override fun persist(uri: String): Boolean = try {
        context.contentResolver.takePersistableUriPermission(
            Uri.parse(uri),
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        true
    } catch (e: SecurityException) {
        // يحدث إن لم يمنح المزوّد إذنًا دائمًا — نسجّل السبب بدل إخفائه،
        // والمرفق يبقى صالحًا للجلسة الحالية فقط.
        Log.w(TAG, "cannot persist read permission for attachment: $uri", e)
        false
    }

    override fun release(uris: List<String>) {
        uris.forEach { value ->
            try {
                context.contentResolver.releasePersistableUriPermission(
                    Uri.parse(value),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: SecurityException) {
                Log.d(TAG, "attachment had no persisted permission: $value")
            }
        }
    }

    private companion object {
        const val TAG = "UriPermissionVault"
    }
}
