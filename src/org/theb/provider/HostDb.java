package org.theb.provider;

import android.net.ContentURI;
import android.provider.BaseColumns;

public final class HostDb {
	public static final class Hosts implements BaseColumns {
		public static final ContentURI CONTENT_URI 
			= ContentURI.create("content://org.theb.provider.HostDb/hosts");

		public static final String DEFAULT_SORT_ORDER = "hostname DESC";

		public static final String USERNAME = "username";
		public static final String HOSTNAME = "hostname";
		public static final String PORT = "port";
		public static final String HOSTKEY = "hostkey";
	}
}
