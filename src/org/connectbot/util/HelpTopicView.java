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
package org.connectbot.util;

import org.connectbot.HelpActivity;

import android.content.Context;
import android.util.AttributeSet;
import android.webkit.WebSettings;
import android.webkit.WebView;

/**
 * @author Kenny Root
 *
 */
public class HelpTopicView extends WebView {
	public HelpTopicView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		initialize();
	}

	public HelpTopicView(Context context, AttributeSet attrs) {
		super(context, attrs);
		initialize();
	}

	public HelpTopicView(Context context) {
		super(context);
		initialize();
	}
	
	private void initialize() {
		WebSettings wSet = getSettings();
		wSet.setLayoutAlgorithm(WebSettings.LayoutAlgorithm.NARROW_COLUMNS);
		wSet.setUseWideViewPort(false);
	}
	
	public HelpTopicView setTopic(String topic) {
		String path = String.format("file:///android_asset/%s/%s%s",
				HelpActivity.HELPDIR, topic, HelpActivity.SUFFIX);
		loadUrl(path);
		
		computeScroll();
		
		return this;
	}
}
