/*
 * Copyright (C) 2007 Kenny Root (kenny at the-b.org)
 * 
 * This file is part of Connectbot.
 *
 *  Connectbot is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Connectbot is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Connectbot.  If not, see <http://www.gnu.org/licenses/>.
 */
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
