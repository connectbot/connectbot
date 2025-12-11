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

package org.connectbot.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import org.connectbot.data.ConnectBotDatabase

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    private const val DATABASE_NAME = "connectbot.db"

    @Provides
    @Singleton
    fun provideConnectBotDatabase(@ApplicationContext context: Context): ConnectBotDatabase {
        return Room.databaseBuilder(
            context,
            ConnectBotDatabase::class.java,
            DATABASE_NAME
        )
            .addMigrations(ConnectBotDatabase.MIGRATION_2_3)
            .build()
    }

    @Provides
    fun provideHostDao(database: ConnectBotDatabase) = database.hostDao()

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
