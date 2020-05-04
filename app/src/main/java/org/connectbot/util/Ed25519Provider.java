/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2016 Kenny Root, Jeffrey Sharkey
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

import java.security.Provider;
import java.security.Security;

import net.i2p.crypto.eddsa.KeyFactory;
import net.i2p.crypto.eddsa.KeyPairGenerator;

/**
 * Created by kenny on 6/8/16.
 */

public class Ed25519Provider extends Provider {
	private static final String NAME = "Ed25519Provider";

	private static final Object sInitLock = new Object();
	private static boolean sInitialized = false;

	/**
	 * Constructs a new instance of the Ed25519Provider.
	 */
	public Ed25519Provider() {
		super(NAME, 1.0, "Provider wrapping eddsa classes");

		put("KeyPairGenerator.Ed25519", KeyPairGenerator.class.getName());
		put("KeyFactory.Ed25519", KeyFactory.class.getName());
	}

	public static void insertIfNeeded() {
		synchronized (sInitLock) {
			if (!sInitialized) {
				Security.addProvider(new Ed25519Provider());
				sInitialized = true;
			}
		}
	}
}
