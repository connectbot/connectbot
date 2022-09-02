package org.connectbot.util;

/*
 * TransferThread Class for VX ConnectBot
 * Copyright 2012 Martin Matuska
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

import java.util.StringTokenizer;

import org.connectbot.service.TerminalBridge;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

public class TransferThread extends Thread {
	public final static String TAG = "TransferThread";
	private final Activity activity;
	private final SharedPreferences prefs;
	private String dialogMessage = null;
	private Handler handler = new Handler();
	private TerminalBridge bridge;
	private String files, destName, destFolder;
	private ProgressDialog progress = null;
	private boolean upload;
	private Toast progressToast = null;

	public TransferThread(Activity activity, Handler handler) {
		this.activity = activity;
//		this.handler = handler;
		this.prefs = PreferenceManager.getDefaultSharedPreferences(this.activity);
	}

	public void setProgressDialogMessage(String message) {
		this.dialogMessage = message;
	}

	public void download(TerminalBridge bridge, String files, String destName, String destFolder) {
		this.bridge = bridge;
		this.files = files;
		this.destName = destName;
		this.destFolder = destFolder;
		this.upload = false;
		this.configureProgressDialog();
		this.start();
	}

	public void upload(TerminalBridge bridge, String files, String destName, String destFolder) {
		this.bridge = bridge;
		this.files = files;
		this.destName = destName;
		this.destFolder = destFolder;
		this.upload = true;
		this.configureProgressDialog();
		this.start();
	}

	@Override
	public void run() {
		if (this.activity == null || this.handler == null || this.bridge == null)
			return;

		Log.d(TAG, "Requested " + (upload ? "upload" : "download") + " of [" + files + "]");
		Resources res = activity.getResources();
		String fail = "";
		try {
			StringTokenizer fileSet = new StringTokenizer(files, "\n");
			while (fileSet.hasMoreTokens()) {
				String file = fileSet.nextToken();
				final String newMessage = upload ? "Uploading file" : "Downloading file";
				handler.post(new Runnable() {
					public void run() {
						if (prefs.getBoolean("background", true)) {
							if (progressToast == null)
								progressToast = Toast.makeText(activity, newMessage, Toast.LENGTH_LONG);
							else
								progressToast.setText(newMessage);
							progressToast.show();
						} else if (progress != null) {
							progress.setMessage(newMessage);
						}
					}
				});
				boolean success = (upload ? bridge.uploadFile(file, destName, destFolder, null) : bridge.downloadFile(file, destFolder));
				if (!success)
					fail += " " + file;
			}
		} finally {
			final String failMessage = (fail.length() == 0 ? null : "Failed");
			final String sucMessage = "Complete";
			handler.post(new Runnable() {
				public void run() {
					if (progress != null)
						progress.dismiss();

					if (prefs.getBoolean("background", true)) {
							Toast.makeText(activity,
								failMessage != null ? failMessage : sucMessage,
								Toast.LENGTH_LONG)
								.show();
					} else if (failMessage != null) {
						new AlertDialog.Builder(activity)
							.setMessage(failMessage)
							.setNegativeButton(android.R.string.ok, null).create().show();
					}
				}
			});
		}
	}

	private void configureProgressDialog() {
		if (dialogMessage != null)
			progress = fileProgressDialog(activity, this.dialogMessage);
		else
			progress = null;
	}

	private ProgressDialog fileProgressDialog(Activity activity, String message) {
		ProgressDialog progress = new ProgressDialog(activity);
		progress.setIndeterminate(true);
		progress.setMessage(message);
		progress.setCancelable(false);
		progress.show();
		return progress;
	}
}