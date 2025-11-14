package org.connectbot.util;

/**
 * Constants for Host management.
 */
public class HostConstants {
	/**
	 * Legacy database names for backup purposes.
	 * Note: The actual database is now managed by Room as ConnectBot.db,
	 * but this constant is kept for backward compatibility with backups.
	 */
	public final static String LEGACY_DB_NAME = "hosts";

	/*
	 * Possible colors for hosts in the list.
	 */
	public final static String COLOR_RED = "red";
	public final static String COLOR_GREEN = "green";
	public final static String COLOR_BLUE = "blue";
	public final static String COLOR_GRAY = "gray";

	/*
	 * Possible keys for what is send when backspace is pressed.
	 */
	public final static String DELKEY_DEL = "del";
	public final static String DELKEY_BACKSPACE = "backspace";

	public final static int DEFAULT_FG_COLOR = 7;
	public final static int DEFAULT_BG_COLOR = 0;

	/*
	 * Port forward types
	 */
	public final static String PORTFORWARD_LOCAL = "local";
	public final static String PORTFORWARD_REMOTE = "remote";
	public final static String PORTFORWARD_DYNAMIC4 = "dynamic4";
	public final static String PORTFORWARD_DYNAMIC5 = "dynamic5";

	/*
	 * Auth agent usage
	 */
	public final static String AUTHAGENT_NO = "no";
	public final static String AUTHAGENT_CONFIRM = "confirm";
	public final static String AUTHAGENT_YES = "yes";

	/*
	 * Old database field names
	 */
	public static final String FIELD_HOST_PROTOCOL = "protocol";
	public static final String FIELD_HOST_HOSTNAME = "hostname";
	public static final String FIELD_HOST_PORT = "port";
	public static final String FIELD_HOST_USERNAME = "username";
	public static final String FIELD_HOST_NICKNAME = "nickname";

	/*
	 * Magic pubkey IDs
	 */
	public final static long PUBKEYID_NEVER = -2;
	public final static long PUBKEYID_ANY = -1;
}
