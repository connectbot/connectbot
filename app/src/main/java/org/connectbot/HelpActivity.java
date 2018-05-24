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

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

/**
 * @author Kenny Root
 *
 */
public class HelpActivity extends AppCompatActivity {
	private LayoutInflater inflater = null;


	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.act_help);

		Button hintsButton = findViewById(R.id.hints_button);
		hintsButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(HelpActivity.this, HintsActivity.class);
				HelpActivity.this.startActivity(intent);
			}
		});

		inflater = LayoutInflater.from(this);
		Button shortcutsButton = findViewById(R.id.shortcuts_button);
		shortcutsButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				@SuppressLint("InflateParams")  // Dialogs do not have a parent view.
				final View shortcuts = inflater.inflate(R.layout.dia_keyboard_shortcuts,
						null, false);

				new android.support.v7.app.AlertDialog.Builder(
								HelpActivity.this, R.style.AlertDialogTheme)
						.setView(shortcuts)
						.setTitle(R.string.keyboard_shortcuts)
						.show();
			}
		});

		Button eulaButton = findViewById(R.id.eula_button);
		eulaButton.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				Intent intent = new Intent(HelpActivity.this, EulaActivity.class);
				HelpActivity.this.startActivity(intent);
			}
		});
	}
}
