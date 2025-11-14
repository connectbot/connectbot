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

package org.connectbot.util;

/**
 * Constants for SSH public/private key management.
 */
public class PubkeyConstants {
	/**
	 * Legacy database names for backup purposes.
	 * Note: The actual database is now managed by Room as ConnectBot.db,
	 * but this constant is kept for backward compatibility with backups.
	 */
	public static final String LEGACY_PUBKEYS_DB_NAME = "pubkeys";

	/**
	 * Key types supported by ConnectBot.
	 */
	public static final String KEY_TYPE_RSA = "RSA";
	public static final String KEY_TYPE_DSA = "DSA";
	public static final String KEY_TYPE_EC = "EC";
	public static final String KEY_TYPE_ED25519 = "Ed25519";
	public static final String KEY_TYPE_IMPORTED = "IMPORTED";
}
