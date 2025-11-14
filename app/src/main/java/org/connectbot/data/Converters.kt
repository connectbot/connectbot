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

import androidx.room.TypeConverter
import org.connectbot.data.entity.KeyStorageType

/**
 * Room type converters for custom types used in database entities.
 */
class Converters {
    @TypeConverter
    fun fromKeyStorageType(value: KeyStorageType): String {
        return value.name
    }

    @TypeConverter
    fun toKeyStorageType(value: String): KeyStorageType {
        return KeyStorageType.valueOf(value)
    }
}
