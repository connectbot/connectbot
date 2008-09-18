package org.connectbot.util;

import org.connectbot.R;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalManager;

import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Paint;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.SimpleCursorAdapter.ViewBinder;

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
	
	public boolean isConnected(Cursor cursor) {
		// always disconnected if we dont have backend service
		if(this.manager == null) return false;
		
		// otherwise pull out nickname and check if active 
		if(COL_NICKNAME == -1)
			COL_NICKNAME = cursor.getColumnIndexOrThrow(HostDatabase.FIELD_HOST_NICKNAME);
		
		String nickname = cursor.getString(COL_NICKNAME);
		return (this.manager.findBridge(nickname) != null);
		
	}
	
	public boolean setViewValue(View view, Cursor cursor, int columnIndex) {

		switch(view.getId()) {
		case android.R.id.icon:
			// set icon state based on status from backend service
			ImageView icon = (ImageView)view;
			if(this.isConnected(cursor)) {
				icon.setImageState(new int[] { android.R.attr.state_checked }, true);
			} else {
				icon.setImageState(new int[] { }, true);
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
				text1.setTextColor(chosen);
				text2.setTextColor(chosen);
			} else {
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
