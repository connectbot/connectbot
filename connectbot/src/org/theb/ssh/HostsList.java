/*
 * Copyright (C) 2007 Kenny Root (kenny at the-b.org)
 * 
 * This file is part of Connectbot.
 *
 *  Connectbot is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Connectbot is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Connectbot.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.theb.ssh;

import java.util.concurrent.Semaphore;

import org.connectbot.Console;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalManager;
import org.connectbot.R;
import org.theb.provider.HostDb;

import com.trilead.ssh2.Connection;

import android.app.Dialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.view.View;
import android.view.WindowManager;
import android.view.View.MeasureSpec;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class HostsList extends ListActivity {
	public static final int CONNECT_ID = Menu.FIRST;
	public static final int EDIT_ID = Menu.FIRST + 1;
	public static final int DELETE_ID = Menu.FIRST + 2;
	public static final int INSERT_ID = Menu.FIRST + 3;
	public static final int PREFERENCES_ID = Menu.FIRST + 4;
	public static final int ABOUT_ID = Menu.FIRST + 5;
	
	// Preferences submenu
	public static final int PUBKEY_ID = SubMenu.FIRST + 4;
	
	private static final String[] PROJECTION = new String[] {
		HostDb.Hosts._ID,
		HostDb.Hosts.HOSTNAME,
		HostDb.Hosts.USERNAME, 
		HostDb.Hosts.PORT,
		HostDb.Hosts.NICKNAME
	};
	
	private Cursor mCursor;
	
	/**
	 * @author kenny
	 * Imparts a more informative view of the host list.
	 * 
	 * Displays as "username@hostname:port" but only includes the port if it is
	 * not on the default port 22.
	 */
	public class HostListCursorAdapter extends SimpleCursorAdapter {

		public HostListCursorAdapter(Context context, int layout, Cursor c,
				String[] from, int[] to) {
			super(context, layout, c, from, to);
		}
		
		@Override
		 public void bindView(View view, Context context, Cursor cursor) {
			String label;
			TextView textView = (TextView) view;

//			label = cursor.getString(2) + "@" + cursor.getString(1);
//			int port = cursor.getInt(3);
//			if (port != 22) {
//				label = label + ":" + String.valueOf(port);
//			}
			
			label = cursor.getString(4);
			textView.setText(label);
		}
		
	}

	public TerminalManager bound = null;
	
    private ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			Log.d(this.getClass().toString(), "yay we bound to our terminalmanager");
			bound = ((TerminalManager.TerminalBinder) service).getService();
			
			// TODO: update our green bulb icons by checking for existing bridges
			// open up some test sessions
//			try {
//				bound.openConnection("192.168.254.230", 22, "connectbot", "b0tt", "screen", 100);
//				bound.openConnection("192.168.254.230", 22, "connectbot", "b0tt", "screen", 100);
//				bound.openConnection("192.168.254.230", 22, "connectbot", "b0tt", "screen", 100);
//			} catch(Exception e) {
//				e.printStackTrace();
//			}
			
		}

		public void onServiceDisconnected(ComponentName className) {
			Log.d(this.getClass().toString(), "oops our terminalmanager was lost");
			bound = null;
		}
	};
	
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);	
        
        // start the terminal manager service and bind locally
		this.startService(new Intent(this, TerminalManager.class));
		this.bindService(new Intent(this, TerminalManager.class), connection, Context.BIND_AUTO_CREATE);

        
        //setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);
        
        Intent intent = getIntent();
        if (intent.getData() == null) {
        	intent.setData(HostDb.Hosts.CONTENT_URI);
        }
        
        //setupListStripes();
        
        mCursor = managedQuery(getIntent().getData(), PROJECTION, null, null);

        ListAdapter adapter = new HostListCursorAdapter(this,
                android.R.layout.simple_list_item_1, mCursor,
                new String[] {HostDb.Hosts.HOSTNAME}, new int[] {android.R.id.text1});

        setListAdapter(adapter);
    }
    
//    /**
//     * Add stripes to the list view.
//     */
//    private void setupListStripes() {
//        // Get Drawables for alternating stripes
//        Drawable[] lineBackgrounds = new Drawable[2];
//        
//        lineBackgrounds[0] = getResources().getDrawable(R.drawable.even_stripe);
//        lineBackgrounds[1] = getResources().getDrawable(R.drawable.odd_stripe);
//
//        // Make and measure a sample TextView of the sort our adapter will
//        // return
//        View view = getViewInflate().inflate(
//                android.R.layout.simple_list_item_1, null, null);
//
//        TextView v = (TextView) view.findViewById(android.R.id.text1);
//        v.setText("X");
//        // Make it 100 pixels wide, and let it choose its own height.
//        v.measure(MeasureSpec.makeMeasureSpec(View.MeasureSpec.EXACTLY, 100),
//                MeasureSpec.makeMeasureSpec(View.MeasureSpec.UNSPECIFIED, 0));
//        int height = v.getMeasuredHeight();
//        getListView().setStripes(lineBackgrounds, height);
//    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        // This is our one standard application action -- inserting a
        // new host into the list.
        menu.add(0, INSERT_ID, INSERT_ID, R.string.menu_insert)
        	.setShortcut('3', 'a');

        // The preferences link allows users to e.g. set the pubkey
        SubMenu prefs = menu.addSubMenu(0, 0, PREFERENCES_ID, R.string.menu_preferences);
        prefs.add(0, PUBKEY_ID, Menu.NONE, R.string.menu_pubkey)
        	.setShortcut('4', 'p');
        
        // This links to the about dialog for the program.
        menu.add(0, ABOUT_ID, ABOUT_ID, R.string.menu_about);
        
        // Generate any additional actions that can be performed on the
        // overall list.  In a normal install, there are no additional
        // actions found here, but this allows other applications to extend
        // our menu with their own actions.
        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(
            Menu.CATEGORY_ALTERNATIVE, 0, Menu.NONE, new ComponentName(this, HostsList.class),
            null, intent, 0, null);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        final boolean haveItems = mCursor.getCount() > 0;

        // If there are any notes in the list (which does not necessarily imply one of
        // them is selected), then we need to generate the actions that
        // can be performed on the current selection.  This will be a combination
        // of our own specific actions along with any extensions that can be
        // found.
        if (haveItems && getSelectedItemId() >= 0) {
            // This is the selected item.
        	Uri uri = ContentUris.withAppendedId(getIntent().getData(), getSelectedItemId());

            // Build menu...  always starts with the PICK action...
            Intent[] specifics = new Intent[1];
            specifics[0] = new Intent(Intent.ACTION_PICK, uri);
            MenuItem[] items = new MenuItem[1];

            // ... is followed by whatever other actions are available...
            Intent intent = new Intent(null, uri);
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
            menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, Menu.NONE, null, specifics,
                                  intent, 0, items);

            // ... and ends with the delete command.
            menu.add(Menu.CATEGORY_ALTERNATIVE, DELETE_ID, DELETE_ID, R.string.menu_delete)
            	.setShortcut('2', 'd');

            // Give a shortcut to the connect action.
            if (items[0] != null) {
                items[0].setShortcut('1', 'c');
            }
        } else {
            menu.removeGroup(Menu.CATEGORY_ALTERNATIVE);
        }

        // Make sure the delete action is disabled if there are no items.
        //menu.setItemShown(DELETE_ID, haveItems);
        return true;
    }
  
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case DELETE_ID:
            deleteItem();
            return true;
        case INSERT_ID:
            insertItem();
            return true;
        case PUBKEY_ID:
        	showPubkey();
        	return true;
        case ABOUT_ID:
        	showAbout();
        	return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void showPubkey() {
    	Intent intent = new Intent(this, Pubkey.class);
    	this.startActivity(intent);
	}

	private void showAbout() {
		Dialog about = new Dialog(this);
		about.setContentView(R.layout.about_dialog);
		about.setTitle(getResources().getString(R.string.app_name)
				+ " "
				+ getResources().getString(R.string.msg_version));
		
		// TODO: update or remove
		// Everything looks cooler when you blur the window behind it.
        //about.getWindow().setFlags(WindowManager.LayoutParams.BLUR_BEHIND_FLAG,
        //        WindowManager.LayoutParams.BLUR_BEHIND_FLAG);
        WindowManager.LayoutParams lp = about.getWindow().getAttributes();
        //lp.tintBehind = 0x60000820;
        about.getWindow().setAttributes(lp);
        
		about.show();
	}

	@Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Uri url = ContentUris.withAppendedId(getIntent().getData(), id);
        
        String action = getIntent().getAction();
        if (Intent.ACTION_PICK.equals(action)
                || Intent.ACTION_GET_CONTENT.equals(action)) {
            // The caller is waiting for us to return a note selected by
            // the user.  The have clicked on one, so return it now.
        	Intent intent = this.getIntent();
	        intent.putExtra(Intent.EXTRA_TEXT, url.toString());
	        setResult(RESULT_OK, intent);
	    } else {
	    	// Launch activity to view/edit the currently selected item
	        //startActivity(new Intent(Intent.ACTION_PICK, url));
	    	
	    	// collect all connection details
			Cursor cursor = managedQuery(url, new String[] { "nickname",
					"username", "hostname", "port", "emulation", "scrollback",
					"hostkey" }, null, null);
			cursor.moveToFirst();
			
			// try finding an already-open bridge for this connection
			final String nickname = cursor.getString(0);
			TerminalBridge bridge = bound.findBridge(nickname);
			if(bridge == null) {
				// too bad, looks like we have to open the bridge ourselves
				final String username = cursor.getString(1);
				final String hostname = cursor.getString(2);
				final int port = cursor.getInt(3);
				final String emulation = cursor.getString(4);
				final int scrollback = cursor.getInt(5);
				final String hostkey = cursor.getString(6);
				
				try {
					// TODO: this is horridly messy lol
					// TODO: finish copying over logic from TrileadConnectionThread here
					
			    	this.startActivityForResult(new Intent(this, PasswordDialog.class), PASSWORD_REQUEST);
			    	
			    	Thread connect = new Thread(new Runnable() {

						public void run() {
							try {
								waitPassword.acquire();
								//Connection conn;
								//bound.openConnection(conn, nickname, emulation, scrollback);
								if (password != null) {
									//bound.openConnection(nickname, hostname, port, username, password, "screen", 100);

									// open the console view and select this specific terminal
									Intent intent = new Intent(HostsList.this, Console.class);
									intent.putExtra(Intent.EXTRA_TEXT, nickname);
									startActivity(intent);
								}
							} catch (Exception e) {
								e.printStackTrace();
							}
							password = null;
						}
			    	});
			    	connect.start();
			    	
				} catch(Exception e) {
					e.printStackTrace();
				}
				
				
			} else {
				// we found an existing terminal, so open it
		    	// open the console view and select this specific terminal
				Intent intent = new Intent(this, Console.class);
				intent.putExtra(Intent.EXTRA_TEXT, nickname);
				this.startActivity(intent);
			}
			
	    	
	    }
    }
	
	public final static int PASSWORD_REQUEST = 42;
	public Semaphore waitPassword = new Semaphore(0);
	public String password = null;
	
    @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if(requestCode == PASSWORD_REQUEST) {
			if (data != null) {
				this.password = data.getStringExtra(Intent.EXTRA_TEXT);
			} else {
				this.password = null;
			}
			this.waitPassword.release();
		}
	}

	private final void deleteItem() {
		mCursor.move(getSelectedItemPosition());
    	Uri uri = ContentUris.withAppendedId(getIntent().getData(), getSelectedItemId());
    	getContentResolver().delete(uri, null, null);
	}

    private final void insertItem() {
        // Launch activity to insert a new item
        startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData()));
    }
}