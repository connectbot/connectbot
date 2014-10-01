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

import java.io.IOException;

import org.connectbot.util.HostDatabase;
import org.connectbot.util.PreferenceConstants;
import org.connectbot.util.PubkeyDatabase;

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.BackupDataOutput;
import android.app.backup.FileBackupHelper;
import android.app.backup.SharedPreferencesBackupHelper;
import android.os.ParcelFileDescriptor;
import android.util.Log;

/**
 * @author kroot
 *
 */
public class BackupAgent extends BackupAgentHelper {
	@Override
	public void onCreate() {
		Log.d("ConnectBot.BackupAgent", "onCreate called");

		SharedPreferencesBackupHelper prefs = new SharedPreferencesBackupHelper(this, getPackageName() + "_preferences");
		addHelper(PreferenceConstants.BACKUP_PREF_KEY, prefs);

		FileBackupHelper hosts = new FileBackupHelper(this, "../databases/" + HostDatabase.DB_NAME);
		addHelper(HostDatabase.DB_NAME, hosts);

		FileBackupHelper pubkeys = new FileBackupHelper(this, "../databases/" + PubkeyDatabase.DB_NAME);
		addHelper(PubkeyDatabase.DB_NAME, pubkeys);

	}

	@Override
	public void onBackup(ParcelFileDescriptor oldState, BackupDataOutput data,
             ParcelFileDescriptor newState) throws IOException {
		synchronized (HostDatabase.dbLock) {
			super.onBackup(oldState, data, newState);
		}
	}

	@Override
	public void onRestore(BackupDataInput data, int appVersionCode,
			ParcelFileDescriptor newState) throws IOException {
		Log.d("ConnectBot.BackupAgent", "onRestore called");

		synchronized (HostDatabase.dbLock) {
			Log.d("ConnectBot.BackupAgent", "onRestore in-lock");

			super.onRestore(data, appVersionCode, newState);
		}
	}
}
