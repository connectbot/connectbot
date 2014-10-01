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

import org.connectbot.util.PreferenceConstants;

import android.app.backup.BackupManager;
import android.content.Context;

/**
 * @author kroot
 *
 */
public abstract class BackupWrapper {
	public static BackupWrapper getInstance() {
		if (PreferenceConstants.PRE_FROYO)
			return PreFroyo.Holder.sInstance;
		else
			return FroyoAndBeyond.Holder.sInstance;
	}

	public abstract void onDataChanged(Context context);

	private static class PreFroyo extends BackupWrapper {
		private static class Holder {
			private static final PreFroyo sInstance = new PreFroyo();
		}

		@Override
		public void onDataChanged(Context context) {
			// do nothing for now
		}
	}

	private static class FroyoAndBeyond extends BackupWrapper {
		private static class Holder {
			private static final FroyoAndBeyond sInstance = new FroyoAndBeyond();
		}

		private static BackupManager mBackupManager;

		@Override
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
}
