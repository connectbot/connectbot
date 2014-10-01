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

import android.content.Context;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.view.View;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

/**
 * @author kenny
 *
 */
public class VolumePreference extends DialogPreference implements OnSeekBarChangeListener {
	/**
	 * @param context
	 * @param attrs
	 */
	public VolumePreference(Context context, AttributeSet attrs) {
		super(context, attrs);

		setupLayout(context, attrs);
	}

	public VolumePreference(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		setupLayout(context, attrs);
	}

	private void setupLayout(Context context, AttributeSet attrs) {
		setPersistent(true);
	}

	@Override
	protected View onCreateDialogView() {
		SeekBar sb = new SeekBar(getContext());

		sb.setMax(100);
		sb.setProgress((int)(getPersistedFloat(
				PreferenceConstants.DEFAULT_BELL_VOLUME) * 100));
		sb.setPadding(10, 10, 10, 10);
		sb.setOnSeekBarChangeListener(this);

		return sb;
	}

	public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
		persistFloat(progress / 100f);
	}

	public void onStartTrackingTouch(SeekBar seekBar) { }

	public void onStopTrackingTouch(SeekBar seekBar) { }
}
