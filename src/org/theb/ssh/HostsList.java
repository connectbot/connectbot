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

import org.theb.provider.HostDb;

import android.app.Dialog;
import android.app.ListActivity;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.SubMenu;
import android.view.View;
import android.view.WindowManager;
import android.view.View.MeasureSpec;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

public class HostsList extends ListActivity {
	public static final int DELETE_ID = Menu.FIRST;
	public static final int INSERT_ID = Menu.FIRST + 1;
	public static final int PREFERENCES_ID = Menu.FIRST + 2;
	public static final int ABOUT_ID = Menu.FIRST + 3;
	
	// Preferences submenu
	public static final int PUBKEY_ID = SubMenu.FIRST + 4;
	
	private static final String[] PROJECTION = new String[] {
		HostDb.Hosts._ID,
		HostDb.Hosts.HOSTNAME,
		HostDb.Hosts.USERNAME, 
		HostDb.Hosts.PORT,
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

			label = cursor.getString(2)
					+ "@"
					+ cursor.getString(1);
			
			int port = cursor.getInt(3);
			if (port != 22) {
				label = label + ":" + String.valueOf(port);
			}
			
			textView.setText(label);
		}
		
	}
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);	
        
        setDefaultKeyMode(SHORTCUT_DEFAULT_KEYS);
        
        Intent intent = getIntent();
        if (intent.getData() == null) {
        	intent.setData(HostDb.Hosts.CONTENT_URI);
        }
        
        setupListStripes();
        
        mCursor = managedQuery(getIntent().getData(), PROJECTION, null, null);

        ListAdapter adapter = new HostListCursorAdapter(this,
                android.R.layout.simple_list_item_1, mCursor,
                new String[] {HostDb.Hosts.HOSTNAME}, new int[] {android.R.id.text1});

        setListAdapter(adapter);
    }
    
    /**
     * Add stripes to the list view.
     */
    private void setupListStripes() {
        // Get Drawables for alternating stripes
        Drawable[] lineBackgrounds = new Drawable[2];
        
        lineBackgrounds[0] = getResources().getDrawable(R.drawable.even_stripe);
        lineBackgrounds[1] = getResources().getDrawable(R.drawable.odd_stripe);

        // Make and measure a sample TextView of the sort our adapter will
        // return
        View view = getViewInflate().inflate(
                android.R.layout.simple_list_item_1, null, null);

        TextView v = (TextView) view.findViewById(android.R.id.text1);
        v.setText("X");
        // Make it 100 pixels wide, and let it choose its own height.
        v.measure(MeasureSpec.makeMeasureSpec(View.MeasureSpec.EXACTLY, 100),
                MeasureSpec.makeMeasureSpec(View.MeasureSpec.UNSPECIFIED, 0));
        int height = v.getMeasuredHeight();
        getListView().setStripes(lineBackgrounds, height);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        // This is our one standard application action -- inserting a
        // new host into the list.
        menu.add(0, INSERT_ID, R.string.menu_insert)
        	.setShortcut('3', 'a');

        // The preferences link allows users to e.g. set the pubkey
        SubMenu prefs = menu.addSubMenu(0, 0, R.string.menu_preferences);
        prefs.add(0, PUBKEY_ID, R.string.menu_pubkey)
        	.setShortcut('4', 'p');
        
        // This links to the about dialog for the program.
        menu.add(0, ABOUT_ID, R.string.menu_about);
        
        // Generate any additional actions that can be performed on the
        // overall list.  In a normal install, there are no additional
        // actions found here, but this allows other applications to extend
        // our menu with their own actions.
        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.ALTERNATIVE_CATEGORY);
        menu.addIntentOptions(
            Menu.ALTERNATIVE, 0, new ComponentName(this, HostsList.class),
            null, intent, 0, null);

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        final boolean haveItems = mCursor.count() > 0;

        // If there are any notes in the list (which implies that one of
        // them is selected), then we need to generate the actions that
        // can be performed on the current selection.  This will be a combination
        // of our own specific actions along with any extensions that can be
        // found.
        if (haveItems) {
            // This is the selected item.
        	Uri uri = ContentUris.withAppendedId(getIntent().getData(), getSelectedItemId());

            // Build menu...  always starts with the PICK action...
            Intent[] specifics = new Intent[1];
            specifics[0] = new Intent(Intent.PICK_ACTION, uri);
            Menu.Item[] items = new Menu.Item[1];

            // ... is followed by whatever other actions are available...
            Intent intent = new Intent(null, uri);
            intent.addCategory(Intent.SELECTED_ALTERNATIVE_CATEGORY);
            menu.addIntentOptions(Menu.SELECTED_ALTERNATIVE, 0, null, specifics,
                                  intent, 0, items);

            // ... and ends with the delete command.
            menu.add(Menu.SELECTED_ALTERNATIVE, DELETE_ID, R.string.menu_delete)
            	.setShortcut('2', 'd');

            // Give a shortcut to the connect action.
            if (items[0] != null) {
                items[0].setShortcut('1', 'c');
            }
        } else {
            menu.removeGroup(Menu.SELECTED_ALTERNATIVE);
        }

        // Make sure the delete action is disabled if there are no items.
        menu.setItemShown(DELETE_ID, haveItems);
        return true;
    }
  
    @Override
    public boolean onOptionsItemSelected(Menu.Item item) {
        switch (item.getId()) {
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
        lp.tintBehind = 0x60000820;
        about.getWindow().setAttributes(lp);
        
		about.show();
	}

	@Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Uri url = ContentUris.withAppendedId(getIntent().getData(), getSelectedItemId());
        
        String action = getIntent().getAction();
        if (Intent.PICK_ACTION.equals(action)
                || Intent.GET_CONTENT_ACTION.equals(action)) {
            // The caller is waiting for us to return a note selected by
            // the user.  The have clicked on one, so return it now.
            setResult(RESULT_OK, url.toString());
        } else {
            // Launch activity to view/edit the currently selected item
            startActivity(new Intent(Intent.PICK_ACTION, url));
        }
    }

    private final void deleteItem() {
    	mCursor.moveTo(getSelectedItemPosition());
        mCursor.deleteRow();
    }

    private final void insertItem() {
        // Launch activity to insert a new item
        startActivity(new Intent(Intent.INSERT_ACTION, getIntent().getData()));
    }
}