/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root, Jeffrey Sharkey
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

import android.content.Context;
import android.content.res.TypedArray;
import android.support.v7.preference.DialogPreference;
import android.util.AttributeSet;

/**
 * @author Kenny Root
 *
 */
public class VolumePreference extends DialogPreference {
	private int mVolume = (int) PreferenceConstants.DEFAULT_BELL_VOLUME * 100;

	public VolumePreference(Context context) {
		this(context, null);
	}

	public VolumePreference(Context context, AttributeSet attrs) {
		this(context, attrs, R.attr.dialogPreferenceStyle);
	}

	public VolumePreference(Context context, AttributeSet attrs, int defStyleAttr) {
		this(context, attrs, defStyleAttr, defStyleAttr);
	}

	public VolumePreference(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
		super(context, attrs, defStyleAttr, defStyleRes);

		setPersistent(true);
	}

	@Override
	public int getDialogLayoutResource() {
		return R.layout.volume_preference_dialog_layout;
	}

	@Override
	protected Object onGetDefaultValue(TypedArray a, int index) {
		return a.getInt(index, 100);
	}

	@Override
	protected void onSetInitialValue(boolean restorePersistedValue, Object defaultValue) {
		setVolume(restorePersistedValue ?
				(int) (getPersistedFloat(mVolume) * 100) : (int) defaultValue);
	}

	public int getVolume() {
		return mVolume;
	}

	public void setVolume(int volume) {
		mVolume = volume;

		persistFloat(mVolume / 100f);
	}
}
