/**
 *
 */
package org.connectbot.util;

/**
 * @author Kenny Root
 *
 */
public class EastAsianWidth {
	public static boolean available = false;

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
			available = true;
		} catch (Exception e) {
			// Failure
		} catch (UnsatisfiedLinkError e1) {
			// Failure
		}
	}
}
