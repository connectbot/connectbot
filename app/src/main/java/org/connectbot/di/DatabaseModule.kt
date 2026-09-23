/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025-2026 Kenny Root
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.di

import android.content.Context
import androidx.preference.PreferenceManager
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import org.connectbot.data.ConnectBotDatabase
import org.connectbot.data.EntityIds
import org.connectbot.data.keyboard.KeyboardDefaults
import org.connectbot.data.sync.SyncDatabase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DATABASE_NAME = "connectbot.db"

    @Provides
    @Singleton
    fun provideConnectBotDatabase(@ApplicationContext context: Context): ConnectBotDatabase = Room.databaseBuilder(
        context,
        ConnectBotDatabase::class.java,
        DATABASE_NAME,
    )
        .addMigrations(ConnectBotDatabase.MIGRATION_4_5, ConnectBotDatabase.MIGRATION_10_11, ConnectBotDatabase.MIGRATION_14_15)
        .addCallback(object : RoomDatabase.Callback() {
            override fun onOpen(db: SupportSQLiteDatabase) {
                SyncDatabase.install(db)
                migrateExternalIds(context, db)
            }

            override fun onCreate(db: SupportSQLiteDatabase) {
                super.onCreate(db)
                KeyboardDefaults.seed(db)
                // Create default profile on fresh database creation
                db.execSQL(
                    """
                        INSERT INTO profiles (id, name, color_scheme_id, font_size, del_key, encoding, emulation)
                        VALUES ('${EntityIds.DEFAULT_PROFILE}', 'Default', '${EntityIds.DEFAULT_SCHEME}', 10, 'del', 'UTF-8', 'xterm-256color')
                    """.trimIndent(),
                )
            }
        })
        .build()

    private fun migrateExternalIds(context: Context, db: SupportSQLiteDatabase) {
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='legacy_id_map'").use {
            if (!it.moveToFirst()) return
        }
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val oldProfile = prefs.all["defaultProfileId"] as? Long
        if (oldProfile != null) {
            val mapped = db.query("SELECT uuid FROM legacy_id_map WHERE entity='profiles' AND old_id=?", arrayOf(oldProfile)).use { if (it.moveToFirst()) it.getString(0) else EntityIds.DEFAULT_PROFILE.toString() }
            check(prefs.edit().putString("defaultProfileId", mapped).commit())
        }
        val passwords = context.getSharedPreferences("secure_host_passwords", Context.MODE_PRIVATE)
        db.query("SELECT old_id,uuid FROM legacy_id_map WHERE entity='hosts'").use { cursor ->
            while (cursor.moveToNext()) {
                val oldKey = "password_${cursor.getLong(0)}"
                val value = passwords.getString(oldKey, null) ?: continue
                val newKey = "password_${cursor.getString(1)}"
                val editor = passwords.edit()
                if (!passwords.contains(newKey)) editor.putString(newKey, value)
                check(editor.remove(oldKey).commit())
            }
        }
    }

    @Provides
    fun provideHostDao(database: ConnectBotDatabase) = database.hostDao()

    @Provides
    fun provideAutomationActionDao(database: ConnectBotDatabase) = database.automationActionDao()

    @Provides
    fun providePubkeyDao(database: ConnectBotDatabase) = database.pubkeyDao()

    @Provides
    fun providePortForwardDao(database: ConnectBotDatabase) = database.portForwardDao()

    @Provides
    fun provideKnownHostDao(database: ConnectBotDatabase) = database.knownHostDao()

    @Provides
    fun provideColorSchemeDao(database: ConnectBotDatabase) = database.colorSchemeDao()

    @Provides
    fun provideProfileDao(database: ConnectBotDatabase) = database.profileDao()
}
