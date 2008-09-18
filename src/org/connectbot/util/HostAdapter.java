package org.connectbot.util;


import org.connectbot.R;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;


public class HostAdapter extends BaseAdapter {


	public final Context context;
	public final LayoutInflater inflater;
	public final Cursor source;

	public final static String TABLE_HOSTS = "hosts";
	public final static String FIELD_HOST_NICKNAME = "nickname";
	public final static String FIELD_HOST_USERNAME = "username";
	public final static String FIELD_HOST_HOSTNAME = "hostname";
	public final static String FIELD_HOST_PORT = "port";
	public final static String FIELD_HOST_HOSTKEY = "hostkey";
	public final static String FIELD_HOST_CONNECTED = "connected";
	
	public final int COL_ID, COL_NICKNAME, COL_USERNAME, COL_HOSTNAME, COL_CONNECTED, COL_COLOR;
	
	public final ColorStateList red, green, blue;
	
	public HostAdapter(Context context, Cursor source) {
		this.context = context;
		this.inflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		this.source = source;
		
		this.COL_ID = source.getColumnIndexOrThrow("_id");
		this.COL_NICKNAME = source.getColumnIndexOrThrow(HostDatabase.FIELD_HOST_NICKNAME);
		this.COL_USERNAME = source.getColumnIndexOrThrow(HostDatabase.FIELD_HOST_USERNAME);
		this.COL_HOSTNAME = source.getColumnIndexOrThrow(HostDatabase.FIELD_HOST_HOSTNAME);
		this.COL_CONNECTED = source.getColumnIndexOrThrow(HostDatabase.FIELD_HOST_LASTCONNECT);
		this.COL_COLOR = source.getColumnIndexOrThrow(HostDatabase.FIELD_HOST_COLOR);
	
		Resources res = this.context.getResources();
		this.red = res.getColorStateList(R.color.red);
		this.green = res.getColorStateList(R.color.green);
		this.blue = res.getColorStateList(R.color.blue);

	}
	
	public Object getItem(int position) {
		source.moveToPosition(position);
		return source;
	}

	public boolean hasStableIds() {
		return true;
	}

	public int getCount() {
		return source.getCount();
	}
	 
	public long getItemId(int position) {
		return position;
	}
	
	protected ColorStateList resolve(String color) {
		if(HostDatabase.COLOR_RED.equals(color)) return this.red;
		if(HostDatabase.COLOR_GREEN.equals(color)) return this.green;
		if(HostDatabase.COLOR_BLUE.equals(color)) return this.blue;
		return null;
	}
	
	public synchronized View getView(int position, View convertView, ViewGroup parent) {
		
		this.source.moveToPosition(position);
		
		if(convertView == null) {
			convertView = this.inflater.inflate(R.layout.item_host, parent, false);
		}
		
		String nice = "never";
		int minutes = ((int)(System.currentTimeMillis() / 1000) - source.getInt(COL_CONNECTED)) / 60;
		if(minutes > 0) {
			nice = String.format("%d minutes ago", minutes);
			if(minutes >= 60) {
				int hours = minutes / 60;
				nice = String.format("%d hours ago", hours);
				if(hours >= 24) {
					int days = hours / 24;
					nice = String.format("%d days ago", days);
				}
			}
		}
		
		boolean connected = true;
		
		TextView title = (TextView)convertView.findViewById(android.R.id.text1);
		title.setText(source.getString(COL_NICKNAME));
		
		TextView caption = (TextView)convertView.findViewById(android.R.id.text2);
		caption.setText(String.format("%s%s", nice, connected ? ", connected" : ""));
		
		// correctly update text color as needed
		title.setTextAppearance(context, android.R.attr.textAppearanceLarge);
		caption.setTextAppearance(context, android.R.attr.textAppearanceSmall);
		ColorStateList resolved = this.resolve(source.getString(COL_COLOR));
		if(resolved != null) {
			title.setTextColor(resolved);
			caption.setTextColor(resolved);
		}
		
		((ImageView)convertView.findViewById(android.R.id.icon)).setImageResource(connected ? android.R.drawable.presence_online : android.R.drawable.presence_offline);
		
		// update icon correctly if service is connected
		
		return convertView;
	}

}