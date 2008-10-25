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

import org.connectbot.R;
import org.connectbot.service.TerminalManager;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.database.Cursor;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.SimpleCursorAdapter.ViewBinder;

/**
 * Binder used to help interpret a HostDatabase cursor into a ListView for
 * display. Specifically, it controls the green "bulb" icon by checking against
 * TerminalManager for status, and shows the last-connected time in a
 * user-friendly format like "6 days ago."
 * 
 * @author jsharkey
 */
public class HostBinder implements ViewBinder {
	
	protected final TerminalManager manager;
	protected final ColorStateList red, green, blue;
	protected int COL_NICKNAME = -1;
	
	public HostBinder(TerminalManager manager, Resources res) {
		this.manager = manager;
		this.red = res.getColorStateList(R.color.red);
		this.green = res.getColorStateList(R.color.green);
		this.blue = res.getColorStateList(R.color.blue);
		
	}
	
	public final static int STATE_UNKNOWN = 1, STATE_CONNECTED = 2, STATE_DISCONNECTED = 3;
	
	/**
	 * Check if we're connected to a terminal with the given nickname.
	 */
	protected int getConnectedState(String nickname) {
		// always disconnected if we dont have backend service
		if(this.manager == null) return STATE_UNKNOWN;
		boolean connected = (this.manager.findBridge(nickname) != null);
		boolean disconnected = (this.manager.disconnected.contains(nickname));
		if(connected) return STATE_CONNECTED;
		if(disconnected) return STATE_DISCONNECTED;
		return STATE_UNKNOWN;
	}
	
	public boolean setViewValue(View view, Cursor cursor, int columnIndex) {

		switch(view.getId()) {
		case android.R.id.icon:
			// set icon state based on status from backend service
			ImageView icon = (ImageView)view;
			
			if(COL_NICKNAME == -1)
				COL_NICKNAME = cursor.getColumnIndexOrThrow(HostDatabase.FIELD_HOST_NICKNAME);
			
			String nickname = cursor.getString(COL_NICKNAME);
			switch(this.getConnectedState(nickname)) {
			case STATE_UNKNOWN:
				icon.setImageState(new int[] { }, true);
				break;
			case STATE_CONNECTED:
				icon.setImageState(new int[] { android.R.attr.state_checked }, true);
				break;
			case STATE_DISCONNECTED:
				icon.setImageState(new int[] { android.R.attr.state_expanded }, true);
				break;
			}
			return true;
			
		case android.R.id.content:
			// set background color correctly
			String color = cursor.getString(columnIndex);
			TextView text1 = (TextView)view.findViewById(android.R.id.text1),
				text2 = (TextView)view.findViewById(android.R.id.text2);
			
			ColorStateList chosen = null;
			if(HostDatabase.COLOR_RED.equals(color)) chosen = this.red;
			if(HostDatabase.COLOR_GREEN.equals(color)) chosen = this.green;
			if(HostDatabase.COLOR_BLUE.equals(color)) chosen = this.blue;

			if(chosen != null) {
				// set color normally if not selected 
				text1.setTextColor(chosen);
				text2.setTextColor(chosen);
			} else {
				// selected, so revert back to default black text
				text1.setTextAppearance(view.getContext(), android.R.attr.textAppearanceLarge);
				text2.setTextAppearance(view.getContext(), android.R.attr.textAppearanceSmall);
			}
			return true;

		case android.R.id.text2:
			// correctly set last-connected time and status
			long created = cursor.getLong(columnIndex);
			long now = System.currentTimeMillis() / 1000;
			
			String nice = "never";
			if(created > 0) {
				int minutes = (int)((now - created) / 60);
				nice = view.getContext().getString(R.string.bind_minutes, minutes);
				if(minutes >= 60) {
					int hours = (minutes / 60);
					nice = view.getContext().getString(R.string.bind_hours, hours);
					if(hours >= 24) {
						int days = (hours / 24);
						nice = view.getContext().getString(R.string.bind_days, days);
					}
				}
			}

			((TextView)view).setText(nice);
			return true;
		}

		// otherwise fall through to other binding methods
		return false;

	}


}
