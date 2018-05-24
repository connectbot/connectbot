/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2017 Kenny Root, Jeffrey Sharkey
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

import org.connectbot.R;

import android.os.Bundle;
import android.support.v7.preference.DialogPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceDialogFragmentCompat;
import android.view.View;
import android.widget.SeekBar;

/**
 * Created by kenny on 2/20/17.
 */

public class VolumePreferenceFragment extends PreferenceDialogFragmentCompat {
	private SeekBar mVolumeBar;

	public VolumePreferenceFragment() {
	}

	public static VolumePreferenceFragment newInstance(Preference preference) {
		VolumePreferenceFragment fragment = new VolumePreferenceFragment();
		Bundle bundle = new Bundle(1);
		bundle.putString("key", preference.getKey());
		fragment.setArguments(bundle);
		return fragment;
	}

	@Override
	protected void onBindDialogView(View view) {
		super.onBindDialogView(view);

		mVolumeBar = view.findViewById(R.id.volume_bar);

		Integer volumeLevel = null;
		DialogPreference preference = getPreference();
		if (preference instanceof VolumePreference) {
			volumeLevel = ((VolumePreference) preference).getVolume();
		}

		if (volumeLevel != null) {
			mVolumeBar.setProgress(volumeLevel);
		}
	}

	@Override
	public void onDialogClosed(boolean positiveResult) {
		if (positiveResult) {
			int volumeLevel = mVolumeBar.getProgress();

			DialogPreference preference = getPreference();
			if (preference instanceof VolumePreference) {
				VolumePreference volumePreference = (VolumePreference) preference;
				if (volumePreference.callChangeListener(volumeLevel)) {
					volumePreference.setVolume(volumeLevel);
				}
			}
		}
	}

}
