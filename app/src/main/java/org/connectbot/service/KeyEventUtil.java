/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2014 Torne Wuff
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
package org.connectbot.service;

import android.annotation.TargetApi;
import android.os.Build;
import android.view.KeyEvent;

public class KeyEventUtil {
	static final char CONTROL_LIMIT = ' ';
	static final char PRINTABLE_LIMIT = '\u007e';
	static final char[] HEX_DIGITS = new char[] {
		'0', '1', '2', '3', '4', '5', '6', '7',
		'8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
	};

	static String printableRepresentation(String source) {
		if (source == null)
			return null;

		final StringBuilder sb = new StringBuilder();
		final int limit = source.length();
		char[] hexbuf = null;
		int pointer = 0;

		sb.append('"');
		while (pointer < limit) {
			int ch = source.charAt(pointer++);
			switch (ch) {
			case '\0':
				sb.append("\\0");
				break;
			case '\t':
				sb.append("\\t");
				break;
			case '\n':
				sb.append("\\n");
				break;
			case '\r':
				sb.append("\\r");
				break;
			case '\"':
				sb.append("\\\"");
				break;
			case '\\':
				sb.append("\\\\");
				break;
			default:
				if (CONTROL_LIMIT <= ch && ch <= PRINTABLE_LIMIT) {
					sb.append((char) ch);
				} else {
					sb.append("\\u");
					if (hexbuf == null)
						hexbuf = new char[4];
					for (int offs = 4; offs > 0; ) {
						hexbuf[--offs] = HEX_DIGITS[ch & 0xf];
						ch >>>= 4;
					}
					sb.append(hexbuf, 0, 4);
				}
			}
		}
		return sb.append('"').toString();
	}

	private static class ClassCompat {
		private static final ClassCompat INSTANCE;
		static {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
				INSTANCE = new HCMR2AndNewer();
			} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR1) {
				INSTANCE = new HCMR1AndNewer();
			} else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
				INSTANCE = new GingerbreadAndNewer();
			} else {
				INSTANCE = new ClassCompat();
			}
		}

		private ClassCompat() {
		}

		public static void appendExtras(StringBuilder d, int keyCode, KeyEvent event) {
			INSTANCE.appendForApi(d, keyCode, event);
		}

		protected void appendForApi(StringBuilder d, int keyCode, KeyEvent event) {
		}

		@TargetApi(9)
		private static class GingerbreadAndNewer extends ClassCompat {
			@Override
			protected void appendForApi(StringBuilder d, int keyCode, KeyEvent event) {
				super.appendForApi(d, keyCode, event);
				d.append(", source=").append(event.getSource());

			}
		}
		@TargetApi(12)
		private static class HCMR1AndNewer extends GingerbreadAndNewer {
			@Override
			protected void appendForApi(StringBuilder d, int keyCode, KeyEvent event) {
				super.appendForApi(d, keyCode, event);
				d.append(", keyCodeToString=").append(KeyEvent.keyCodeToString(keyCode));
			}
		}

		@TargetApi(13)
		private static class HCMR2AndNewer extends HCMR1AndNewer {
			@Override
			protected void appendForApi(StringBuilder d, int keyCode, KeyEvent event) {
				super.appendForApi(d, keyCode, event);
				d.append(", modifiers=").append(Integer.toHexString(event.getModifiers()));
			}
		}
	}

	public static String describeKeyEvent(int keyCode, KeyEvent event) {
		StringBuilder d = new StringBuilder();
		d.append("keyCode=").append(keyCode);
		d.append(", event.toString=").append(event.toString());
		d.append(", action=").append(event.getAction());
		d.append(", characters=").append(printableRepresentation(event.getCharacters()));
		d.append(", deviceId=").append(event.getDeviceId());
		d.append(", displayLabel=").append((int) event.getDisplayLabel());
		d.append(", flags=0x").append(Integer.toHexString(event.getFlags()));
		d.append(", printingKey=").append(event.isPrintingKey());
		d.append(", keyCode=").append(event.getKeyCode());
		d.append(", metaState=0x").append(Integer.toHexString(event.getMetaState()));
		d.append(", number=").append((int) event.getNumber());
		d.append(", scanCode=").append(event.getScanCode());
		d.append(", unicodeChar=").append(event.getUnicodeChar());
		ClassCompat.appendExtras(d, keyCode, event);
		return d.toString();
	}
}
