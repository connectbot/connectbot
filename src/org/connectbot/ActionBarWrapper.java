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

package org.connectbot;

import org.connectbot.util.PreferenceConstants;

import android.app.Activity;
import android.app.ActionBar;

public abstract class ActionBarWrapper {
	public interface OnMenuVisibilityListener {
		public void onMenuVisibilityChanged(boolean isVisible);
	}

	public static ActionBarWrapper getActionBar(Activity activity) {
		if (PreferenceConstants.PRE_HONEYCOMB)
			return new DummyActionBar();
		else
			return new RealActionBar(activity);
	}

	public void hide() {
	}

	public void show() {
	}

	public void addOnMenuVisibilityListener(OnMenuVisibilityListener listener) {
	}

	public void setDisplayHomeAsUpEnabled(boolean showHomeAsUp) {
	}

	private static class DummyActionBar extends ActionBarWrapper {
	}

	private static class RealActionBar extends ActionBarWrapper {
		private final ActionBar actionBar;

		public RealActionBar(Activity activity) {
			actionBar = activity.getActionBar();
		}

		@Override
		public void hide() {
			actionBar.hide();
		}

		@Override
		public void show() {
			actionBar.show();
		}
		
		@Override
		public void addOnMenuVisibilityListener(final OnMenuVisibilityListener listener) {
			actionBar.addOnMenuVisibilityListener(new ActionBar.OnMenuVisibilityListener() {
				public void onMenuVisibilityChanged(boolean isVisible) {
					listener.onMenuVisibilityChanged(isVisible);
				}
			});
		}

		@Override
		public void setDisplayHomeAsUpEnabled(boolean showHomeAsUp) {
			actionBar.setDisplayHomeAsUpEnabled(showHomeAsUp);
		}
	}
}
