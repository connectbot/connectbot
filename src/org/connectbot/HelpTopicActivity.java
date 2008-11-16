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

import org.connectbot.util.HelpTopicView;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * @author Kenny Root
 *
 */
public class HelpTopicActivity extends Activity {
	public final static String TAG = HelpActivity.class.toString();

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		setContentView(R.layout.act_help_topic);
		
		String topic = getIntent().getStringExtra(Intent.EXTRA_TITLE);
		
		this.setTitle(String.format("%s: %s - %s",
				getResources().getText(R.string.app_name),
				getResources().getText(R.string.title_help),
				topic));
		
		HelpTopicView helpTopic = (HelpTopicView) findViewById(R.id.topic_text);
		
        helpTopic.setTopic(topic);
	}
}
