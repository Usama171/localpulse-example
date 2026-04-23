package com.ghostapps.localpulse_example.sync

import android.content.Context
import androidx.core.content.edit

class SyncMetaStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getLastUserSyncEpochMillis(): Long = prefs.getLong(KEY_LAST_USER_SYNC, 0L)

    fun setLastUserSyncEpochMillis(value: Long) {
        prefs.edit { putLong(KEY_LAST_USER_SYNC, value) }
    }

    private companion object {
        const val PREFS_NAME = "sync_meta_store"
        const val KEY_LAST_USER_SYNC = "last_user_sync_ms"
    }
}
