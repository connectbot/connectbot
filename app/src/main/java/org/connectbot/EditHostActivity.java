/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2015 Kenny Root, Jeffrey Sharkey
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

import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;

import org.connectbot.bean.HostBean;

public class EditHostActivity extends AppCompatActivity implements HostEditorFragment.Listener {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_edit_host);

		if (savedInstanceState == null) {
			HostEditorFragment editor = HostEditorFragment.newInstance(null);
			getSupportFragmentManager().beginTransaction()
					.add(R.id.fragment_container, editor).commit();
		}
	}

	@Override
	public void onHostUpdated(HostBean host) {

	}
}
