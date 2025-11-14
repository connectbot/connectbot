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

package org.connectbot.data.migration

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import org.connectbot.data.entity.ColorPalette
import org.connectbot.data.entity.ColorScheme
import org.connectbot.data.entity.Host
import org.connectbot.data.entity.KnownHost
import org.connectbot.data.entity.PortForward

/**
 * Reads data from the legacy HostDatabase (version 27).
 * This is a read-only class that extracts data from the old database format.
 */
class LegacyHostDatabaseReader(private val context: Context) {

    companion object {
        private const val TAG = "LegacyHostDBReader"
        private const val DB_NAME = "hosts"
    }

    /**
     * Reads all hosts from the legacy database.
     */
    fun readHosts(): List<Host> {
        val hosts = mutableListOf<Host>()

        withReadableDatabase { db ->
            db.query(
                "hosts",
                null, // all columns
                null, // no where clause
                null, // no where args
                null, // no group by
                null, // no having
                "nickname ASC" // order by
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    try {
                        val host = cursorToHost(cursor)
                        hosts.add(host)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading host from cursor", e)
                    }
                }
            }
        }

        Log.d(TAG, "Read ${hosts.size} hosts from legacy database")
        return hosts
    }

    /**
     * Reads all port forwards from the legacy database.
     */
    fun readPortForwards(): List<PortForward> {
        val portForwards = mutableListOf<PortForward>()

        withReadableDatabase { db ->
            db.query(
                "portforwards",
                null,
                null,
                null,
                null,
                null,
                "_id ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    try {
                        val portForward = cursorToPortForward(cursor)
                        portForwards.add(portForward)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading port forward from cursor", e)
                    }
                }
            }
        }

        Log.d(TAG, "Read ${portForwards.size} port forwards from legacy database")
        return portForwards
    }

    /**
     * Reads all known hosts from the legacy database.
     * Joins with hosts table to get hostname and port.
     */
    fun readKnownHosts(): List<KnownHost> {
        val knownHosts = mutableListOf<KnownHost>()

        withReadableDatabase { db ->
            // Join with hosts table to get hostname and port
            db.rawQuery(
                """
                SELECT kh._id, kh.hostid, kh.hostkeyalgo, kh.hostkey, h.hostname, h.port
                FROM knownhosts kh
                INNER JOIN hosts h ON kh.hostid = h._id
                ORDER BY kh._id ASC
                """.trimIndent(),
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    try {
                        val knownHost = cursorToKnownHost(cursor)
                        knownHosts.add(knownHost)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading known host from cursor", e)
                    }
                }
            }
        }

        Log.d(TAG, "Read ${knownHosts.size} known hosts from legacy database")
        return knownHosts
    }

    /**
     * Reads all color schemes from the legacy database.
     */
    fun readColorSchemes(): List<ColorScheme> {
        val schemes = mutableListOf<ColorScheme>()

        withReadableDatabase { db ->
            db.query(
                "colorSchemes",
                null,
                null,
                null,
                null,
                null,
                "_id ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    try {
                        val scheme = cursorToColorScheme(cursor)
                        schemes.add(scheme)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading color scheme from cursor", e)
                    }
                }
            }
        }

        Log.d(TAG, "Read ${schemes.size} color schemes from legacy database")
        return schemes
    }

    /**
     * Reads all color palettes from the legacy database.
     */
    fun readColorPalettes(): List<ColorPalette> {
        val palettes = mutableListOf<ColorPalette>()

        withReadableDatabase { db ->
            db.query(
                "colors",
                null,
                null,
                null,
                null,
                null,
                "scheme ASC, number ASC"
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    try {
                        val palette = cursorToColorPalette(cursor)
                        palettes.add(palette)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error reading color palette from cursor", e)
                    }
                }
            }
        }

        Log.d(TAG, "Read ${palettes.size} color palette entries from legacy database")
        return palettes
    }

    private fun cursorToHost(cursor: Cursor): Host {
        val idIndex = cursor.getColumnIndexOrThrow("_id")
        val nicknameIndex = cursor.getColumnIndexOrThrow("nickname")
        val protocolIndex = cursor.getColumnIndexOrThrow("protocol")
        val usernameIndex = cursor.getColumnIndexOrThrow("username")
        val hostnameIndex = cursor.getColumnIndexOrThrow("hostname")
        val portIndex = cursor.getColumnIndexOrThrow("port")
        val hostkeyAlgoIndex = cursor.getColumnIndex("hostkeyalgo")
        val lastConnectIndex = cursor.getColumnIndexOrThrow("lastconnect")
        val colorIndex = cursor.getColumnIndex("color")
        val useKeysIndex = cursor.getColumnIndexOrThrow("usekeys")
        val useAuthAgentIndex = cursor.getColumnIndex("useauthagent")
        val postLoginIndex = cursor.getColumnIndex("postlogin")
        val pubkeyIdIndex = cursor.getColumnIndexOrThrow("pubkeyid")
        val wantSessionIndex = cursor.getColumnIndexOrThrow("wantsession")
        val compressionIndex = cursor.getColumnIndexOrThrow("compression")
        val encodingIndex = cursor.getColumnIndexOrThrow("encoding")
        val stayConnectedIndex = cursor.getColumnIndexOrThrow("stayconnected")
        val quickDisconnectIndex = cursor.getColumnIndex("quickdisconnect")
        val fontSizeIndex = cursor.getColumnIndexOrThrow("fontsize")
        val colorSchemeIdIndex = cursor.getColumnIndex("scheme")
        val delKeyIndex = cursor.getColumnIndexOrThrow("delkey")
        val scrollbackLinesIndex = cursor.getColumnIndex("scrollbacklines")
        val useCtrlAltAsMetaIndex = cursor.getColumnIndex("usectrlaltasmeta")

        return Host(
            id = cursor.getLong(idIndex),
            nickname = cursor.getString(nicknameIndex),
            protocol = cursor.getString(protocolIndex),
            username = cursor.getString(usernameIndex),
            hostname = cursor.getString(hostnameIndex),
            port = cursor.getInt(portIndex),
            hostKeyAlgo = if (hostkeyAlgoIndex >= 0) cursor.getStringOrNull(hostkeyAlgoIndex) else null,
            lastConnect = cursor.getLong(lastConnectIndex),
            color = if (colorIndex >= 0) cursor.getStringOrNull(colorIndex) else null,
            useKeys = cursor.getStringAsBoolean(useKeysIndex),
            useAuthAgent = if (useAuthAgentIndex >= 0) cursor.getStringOrNull(useAuthAgentIndex) else null,
            postLogin = if (postLoginIndex >= 0) cursor.getStringOrNull(postLoginIndex) else null,
            pubkeyId = cursor.getLong(pubkeyIdIndex),
            wantSession = cursor.getStringAsBoolean(wantSessionIndex),
            compression = cursor.getStringAsBoolean(compressionIndex),
            encoding = cursor.getString(encodingIndex),
            stayConnected = cursor.getStringAsBoolean(stayConnectedIndex),
            quickDisconnect = if (quickDisconnectIndex >= 0) cursor.getStringAsBoolean(quickDisconnectIndex) else false,
            fontSize = cursor.getInt(fontSizeIndex),
            colorSchemeId = if (colorSchemeIdIndex >= 0) cursor.getLong(colorSchemeIdIndex) else 1L,
            delKey = cursor.getString(delKeyIndex),
            scrollbackLines = if (scrollbackLinesIndex >= 0) cursor.getInt(scrollbackLinesIndex) else 140,
            useCtrlAltAsMetaKey = if (useCtrlAltAsMetaIndex >= 0) cursor.getStringAsBoolean(useCtrlAltAsMetaIndex) else false
        )
    }

    private fun cursorToPortForward(cursor: Cursor): PortForward {
        val idIndex = cursor.getColumnIndexOrThrow("_id")
        val hostIdIndex = cursor.getColumnIndexOrThrow("hostid")
        val nicknameIndex = cursor.getColumnIndexOrThrow("nickname")
        val typeIndex = cursor.getColumnIndexOrThrow("type")
        val sourcePortIndex = cursor.getColumnIndexOrThrow("sourceport")
        val destAddrIndex = cursor.getColumnIndexOrThrow("destaddr")
        val destPortIndex = cursor.getColumnIndexOrThrow("destport")

        return PortForward(
            id = cursor.getLong(idIndex),
            hostId = cursor.getLong(hostIdIndex),
            nickname = cursor.getString(nicknameIndex),
            type = cursor.getString(typeIndex),
            sourcePort = cursor.getInt(sourcePortIndex),
            destAddr = cursor.getString(destAddrIndex),
            destPort = cursor.getInt(destPortIndex)
        )
    }

    private fun cursorToKnownHost(cursor: Cursor): KnownHost {
        val idIndex = cursor.getColumnIndexOrThrow("_id")
        val hostIdIndex = cursor.getColumnIndexOrThrow("hostid")
        val hostnameIndex = cursor.getColumnIndexOrThrow("hostname")
        val portIndex = cursor.getColumnIndexOrThrow("port")
        val hostKeyAlgoIndex = cursor.getColumnIndexOrThrow("hostkeyalgo")
        val hostKeyIndex = cursor.getColumnIndexOrThrow("hostkey")

        return KnownHost(
            id = cursor.getLong(idIndex),
            hostId = cursor.getLong(hostIdIndex),
            hostname = cursor.getString(hostnameIndex),
            port = cursor.getInt(portIndex),
            hostKeyAlgo = cursor.getString(hostKeyAlgoIndex),
            hostKey = cursor.getBlob(hostKeyIndex)
        )
    }

    private fun cursorToColorScheme(cursor: Cursor): ColorScheme {
        val idIndex = cursor.getColumnIndexOrThrow("_id")
        val nameIndex = cursor.getColumnIndexOrThrow("name")
        val descriptionIndex = cursor.getColumnIndex("description")

        return ColorScheme(
            id = cursor.getInt(idIndex),
            name = cursor.getString(nameIndex),
            isBuiltIn = true, // Legacy schemes are treated as built-in
            description = if (descriptionIndex >= 0) cursor.getString(descriptionIndex) ?: "" else ""
        )
    }

    private fun cursorToColorPalette(cursor: Cursor): ColorPalette {
        val schemeIndex = cursor.getColumnIndexOrThrow("scheme")
        val numberIndex = cursor.getColumnIndexOrThrow("number")
        val valueIndex = cursor.getColumnIndexOrThrow("value")

        return ColorPalette(
            schemeId = cursor.getInt(schemeIndex).toLong(),
            colorIndex = cursor.getInt(numberIndex),
            color = cursor.getInt(valueIndex)
        )
    }

    private inline fun withReadableDatabase(block: (SQLiteDatabase) -> Unit) {
        val dbFile = context.getDatabasePath(DB_NAME)
        if (!dbFile.exists()) {
            Log.w(TAG, "Legacy database file does not exist: ${dbFile.absolutePath}")
            return
        }

        try {
            val db = SQLiteDatabase.openDatabase(
                dbFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )
            try {
                block(db)
            } finally {
                db.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error opening legacy database", e)
            throw MigrationException("Failed to open legacy hosts database: ${e.message}")
        }
    }

    /**
     * Legacy database stored booleans as strings ("true"/"false").
     */
    private fun Cursor.getStringAsBoolean(columnIndex: Int): Boolean {
        val value = getString(columnIndex)
        return value.equals("true", ignoreCase = true)
    }

    private fun Cursor.getStringOrNull(columnIndex: Int): String? {
        return if (isNull(columnIndex)) null else getString(columnIndex)
    }
}
