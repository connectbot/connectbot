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

import java.io.IOException;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;

/**
 * @author Kenny Root
 *
 */
public class HelpActivity extends AppCompatActivity {
	public final static String TAG = "CB.HelpActivity";

	public final static String HELPDIR = "help";
	public final static String SUFFIX = ".html";

	private LayoutInflater inflater = null;


	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.act_help);

		AssetManager am = this.getAssets();
		LinearLayout content = (LinearLayout) findViewById(R.id.topics);

		try {
			for (String name : am.list(HELPDIR)) {
				if (name.endsWith(SUFFIX)) {
					Button button = new Button(this);
					final String topic = name.substring(0, name.length() - SUFFIX.length());
					button.setText(topic);

					button.setOnClickListener(new OnClickListener() {
						public void onClick(View v) {
							Intent intent = new Intent(HelpActivity.this, HelpTopicActivity.class);
							intent.putExtra(Intent.EXTRA_TITLE, topic);
							HelpActivity.this.startActivity(intent);
						}
					});

					content.addView(button);
				}
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			Log.e(TAG, "couldn't get list of help assets", e);
		}

		inflater = LayoutInflater.from(this);
		Button shortcutsButton = new Button(this);
		shortcutsButton.setText(getResources().getString(R.string.keyboard_shortcuts));
		shortcutsButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				final View shortcuts = inflater.inflate(R.layout.dia_keyboard_shortcuts, null, false);
				new AlertDialog.Builder(HelpActivity.this)
						.setView(shortcuts)
						.setTitle(R.string.keyboard_shortcuts)
						.show();
			}
		});
		content.addView(shortcutsButton);

		Button eulaButton = new Button(this);
		eulaButton.setText(getResources().getString(R.string.terms_and_conditions));
		eulaButton.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				Intent intent = new Intent(HelpActivity.this, EulaActivity.class);
				HelpActivity.this.startActivity(intent);
			}
		});
		content.addView(eulaButton);
	}
}
