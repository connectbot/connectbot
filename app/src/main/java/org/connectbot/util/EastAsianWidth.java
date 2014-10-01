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

import android.graphics.Paint;
import android.text.AndroidCharacter;

/**
 * @author Kenny Root
 *
 */
public abstract class EastAsianWidth {
	public static EastAsianWidth getInstance() {
		if (PreferenceConstants.PRE_FROYO)
			return PreFroyo.Holder.sInstance;
		else
			return FroyoAndBeyond.Holder.sInstance;
	}

	/**
	 * @param charArray
	 * @param i
	 * @param position
	 * @param wideAttribute
	 */
	public abstract void measure(char[] charArray, int start, int end,
			byte[] wideAttribute, Paint paint, int charWidth);

	private static class PreFroyo extends EastAsianWidth {
		private static final int BUFFER_SIZE = 4096;
		private float[] mWidths = new float[BUFFER_SIZE];

		private static class Holder {
			private static final PreFroyo sInstance = new PreFroyo();
		}

		@Override
		public void measure(char[] charArray, int start, int end,
				byte[] wideAttribute, Paint paint, int charWidth) {
			paint.getTextWidths(charArray, start, end, mWidths);
			final int N = end - start;
			for (int i = 0; i < N; i++)
				wideAttribute[i] = (byte) (((int)mWidths[i] != charWidth) ?
						AndroidCharacter.EAST_ASIAN_WIDTH_WIDE :
						AndroidCharacter.EAST_ASIAN_WIDTH_NARROW);
		}
	}

	private static class FroyoAndBeyond extends EastAsianWidth {
		private static class Holder {
			private static final FroyoAndBeyond sInstance = new FroyoAndBeyond();
		}

		@Override
		public void measure(char[] charArray, int start, int end,
				byte[] wideAttribute, Paint paint, int charWidth) {
			AndroidCharacter.getEastAsianWidths(charArray, start, end - start, wideAttribute);
		}
	}
}
