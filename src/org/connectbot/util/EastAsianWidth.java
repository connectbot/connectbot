/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root, Jeffrey Sharkey
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

import android.util.Log;

/**
 * @author Kenny Root
 *
 */
public class EastAsianWidth {
	public static boolean useJNI = false;
	private static final String TAG = "ConnectBot.EastAsianWidth";

	/**
	 * @param charArray
	 * @param i
	 * @param position
	 * @param wideAttribute
	 * @param isLegacyEastAsian
	 */
	public native static void measure(char[] charArray, int start, int end,
			byte[] wideAttribute, boolean isLegacyEastAsian);

	static {
		try {
			System.loadLibrary("org_connectbot_util_EastAsianWidth");

			char[] testInput = {(char)0x4EBA};
			byte[] testResult = new byte[1];
			measure(testInput, 0, 1, testResult, true);

			if (testResult[0] == 1)
				useJNI = true;
			else
				Log.d(TAG, "EastAsianWidth JNI measuring not available");
		} catch (Exception e) {
			// Failure
		} catch (UnsatisfiedLinkError e1) {
			// Failure
		}
	}
}
