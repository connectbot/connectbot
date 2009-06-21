/*
	ConnectBot: simple, powerful, open-source SSH client for Android
	Copyright (C) 2007-2008 Kenny Root, Jeffrey Sharkey

	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/
package org.connectbot;

import java.io.IOException;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.LinearLayout;

/**
 * @author Kenny Root
 *
 */
public class HelpActivity extends Activity {
	public final static String TAG = "ConnectBot.HelpActivity";

	public final static String HELPDIR = "help";
	public final static String SUFFIX = ".html";

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.act_help);

		this.setTitle(String.format("%s: %s",
				getResources().getText(R.string.app_name),
				getResources().getText(R.string.title_help)));

		AssetManager am = this.getAssets();
		LinearLayout content = (LinearLayout)this.findViewById(R.id.topics);

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
	}
}
