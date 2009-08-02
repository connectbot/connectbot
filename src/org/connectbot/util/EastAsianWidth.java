/**
 *
 */
package org.connectbot.util;

/**
 * @author Kenny Root
 *
 */
public class EastAsianWidth {
	/**
	 * @param charArray
	 * @param i
	 * @param position
	 * @param wideAttribute
	 * @param isLegacyEastAsian
	 */
	public native static void measure(char[] charArray, int start, int end,
			byte[] wideAttribute, boolean isLegacyEastAsian);
}
