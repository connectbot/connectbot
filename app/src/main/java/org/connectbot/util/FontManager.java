/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2017 Kenny Root, Jeffrey Sharkey
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


import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import android.content.Context;
import android.graphics.Typeface;

public class FontManager {
	private static String FONT_DIRECTORY = "fonts";
	public static String SYSTEM_FONT = "System";
	private static Set<String> FONTS = new HashSet<>(Arrays.asList( "Hack"));

	public static Typeface loadFont(Context context, String fontName) {
		if (!FONTS.contains(fontName) && !isSystemFont(fontName)) {
			throw new IllegalArgumentException(String.format(Locale.getDefault(),
					"%s font not found in FontManager", fontName));
		}

		if (isSystemFont(fontName)) {
			return Typeface.MONOSPACE;
		}

		try {
			Typeface loadedTypeface = Typeface.createFromAsset(context.getAssets(),
					FONT_DIRECTORY + File.separator + fontName + ".ttf");

			if (loadedTypeface == null) {
				throw new IllegalArgumentException(String.format(Locale.getDefault(),
						"Failed to load %s from %s directory", fontName, FONT_DIRECTORY));
			}

			return loadedTypeface;
		} catch (Exception ex) {
			throw new IllegalArgumentException(String.format(Locale.getDefault(),
					"Failed to load %s from %s directory", fontName, FONT_DIRECTORY), ex);
		}
	}

	public static boolean isSystemFont(String fontName) {
		return SYSTEM_FONT.equals(fontName);
	}

	public static List<String> getAvailableFonts() {
		ArrayList<String> availableFonts = new ArrayList<>(FONTS);
		availableFonts.add(0, SYSTEM_FONT);
		return availableFonts;
	}
}
