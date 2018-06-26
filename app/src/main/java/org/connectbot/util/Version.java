/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2018 Kenny Root, Jeffrey Sharkey
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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;
import android.widget.TextView;

public class Version {
	public static final String TAG = "CB/EulaActivity";

	private Version() {
	}

	public static void setVersionText(Context context, TextView textView) {
		try {
			PackageInfo pi = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
			textView.setText(pi.versionName);
		} catch (PackageManager.NameNotFoundException e) {
			Log.i(TAG, "Couldn't get my own package info for some reason");
		}
	}
}
