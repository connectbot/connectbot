/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2025 Kenny Root
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

package org.connectbot.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.connectbot.data.dao.ColorSchemeDao
import org.connectbot.data.dao.HostDao
import org.connectbot.data.dao.KnownHostDao
import org.connectbot.data.dao.PortForwardDao
import org.connectbot.data.dao.PubkeyDao
import org.connectbot.data.entity.ColorPalette
import org.connectbot.data.entity.ColorScheme
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.KnownHost
import org.connectbot.data.entity.PortForward
import org.connectbot.data.entity.Pubkey

/**
 * ConnectBot Room database.
 *
 * This database contains all the tables needed to run ConnectBot:
 * - hosts: SSH/Telnet connection configurations
 * - pubkeys: SSH key pairs with security-conscious backup controls
 * - port_forwards: SSH port forwarding rules
 * - known_hosts: SSH host key verification data
 * - color_schemes: Terminal color scheme metadata
 * - color_palette: Terminal color overrides
 *
 * Migration Strategy:
 * - Version 1: Initial Room schema (migrated from HostDatabase v27 + PubkeyDatabase v2)
 * - Version 2: Added jump_host_id column for ProxyJump support (AutoMigration)
 * - Future versions: Use Room AutoMigration when possible for simple schema changes
 *
 * Security Considerations:
 * - Pubkeys table supports per-key backup control via allowBackup field
 * - Custom BackupAgent filters pubkeys during backup/restore operations
 * - Schema ready for future Android Keystore integration (Phase 2)
 */
@Database(
    entities = [
        Host::class,
        Pubkey::class,
        PortForward::class,
        KnownHost::class,
        ColorScheme::class,
        ColorPalette::class
    ],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2)
    ]
)
@TypeConverters(Converters::class)
abstract class ConnectBotDatabase : RoomDatabase() {

    abstract fun hostDao(): HostDao
    abstract fun pubkeyDao(): PubkeyDao
    abstract fun portForwardDao(): PortForwardDao
    abstract fun knownHostDao(): KnownHostDao
    abstract fun colorSchemeDao(): ColorSchemeDao

    companion object {
        private const val DATABASE_NAME = "connectbot.db"

        // Used for instrumentation tests
        @VisibleForTesting
        private const val TEST_DATABASE_NAME = "connectbot_test.db"

        @Volatile
        private var INSTANCE: ConnectBotDatabase? = null

        /**
         * Get the singleton database instance.
         *
         * @param context Application context
         * @return ConnectBotDatabase instance
         */
        fun getInstance(context: Context): ConnectBotDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ConnectBotDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_LEGACY_TO_1)
                    .build()

                INSTANCE = instance
                instance
            }
        }

        /**
         * Migration from legacy databases (HostDatabase v27 + PubkeyDatabase v2) to Room v1.
         *
         * This migration is triggered when:
         * 1. Old hosts.db and/or pubkeys.db exist
         * 2. New connectbot.db does not exist or is at version 0
         *
         * The actual data migration is handled by DatabaseMigrator service,
         * which runs asynchronously during app startup. This migration object
         * just creates the new schema structure.
         */
        private val MIGRATION_LEGACY_TO_1 = object : Migration(0, 1) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Schema is automatically created by Room
                // Data migration handled by DatabaseMigrator service
            }
        }

        /**
         * Close the database and clear the singleton instance.
         * Used for testing purposes.
         */
        @VisibleForTesting
        fun clearInstance() {
            INSTANCE?.close()
            INSTANCE = null
        }

        /**
         * Create a test database instance for testing with main thread queries
         * allowed. This should only be used in instrumented tests.
         */
        @VisibleForTesting
        fun getTestInstance(context: Context): ConnectBotDatabase {
            clearInstance()
            val instance = Room.databaseBuilder(
                context.applicationContext,
                ConnectBotDatabase::class.java,
                TEST_DATABASE_NAME
            )
                .addMigrations(MIGRATION_LEGACY_TO_1)
                .fallbackToDestructiveMigration(true)
                .allowMainThreadQueries()
                .build()

            INSTANCE = instance
            return instance
        }
    }
}
