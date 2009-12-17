package com.nullwire.trace;

public class G {
	// This must be set by the application - it used to automatically
	// transmit exceptions to the trace server
	public static String FILES_PATH = null;
	public static String APP_PACKAGE = "unknown";
	public static String APP_VERSION = "unknown";
	public static String APP_DESCRIPTION = "unknown";
	public static String PHONE_MODEL = "unknown";
	public static String ANDROID_VERSION = "unknown";
	// Where are the stack traces posted?
	public static String URL = "http://connectbot.the-b.org/trace/";
}
