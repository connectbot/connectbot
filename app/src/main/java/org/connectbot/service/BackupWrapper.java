/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2010 Kenny Root, Jeffrey Sharkey
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

import android.app.backup.BackupManager;
import android.content.Context;

/**
 * This is only invoked on Froyo and beyond.
 */
public class BackupWrapper {
	private static class Holder {
		private static final BackupWrapper sInstance = new BackupWrapper();
	}

	private BackupWrapper() {
	}

	public static BackupWrapper getInstance() {
		return Holder.sInstance;
	}

	private static BackupManager mBackupManager;

	public void onDataChanged(Context context) {
		checkBackupManager(context);
		if (mBackupManager != null) {
			mBackupManager.dataChanged();
		}
	}

	private void checkBackupManager(Context context) {
		if (mBackupManager == null) {
			mBackupManager = new BackupManager(context);
		}
	}
}
