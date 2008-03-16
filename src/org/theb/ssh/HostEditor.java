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

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class HostEditor extends Activity {
	public static final String EDIT_HOST_ACTION =
        "com.theb.ssh.action.EDIT_HOST";

    private static final String[] PROJECTION = new String[] {
    	HostDb.Hosts._ID, // 0
    	HostDb.Hosts.HOSTNAME, // 1
    	HostDb.Hosts.USERNAME, // 2
    	HostDb.Hosts.PORT, // 3
    	HostDb.Hosts.HOSTKEY, // 4
    };
    
    static final int HOSTNAME_INDEX = 1;
    private static final int USERNAME_INDEX = 2;
    private static final int PORT_INDEX = 3;
    // Set up distinct states that the activity can be run in.
    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;
    
	private EditText mHostname;
	private EditText mUsername;
	private EditText mPort;

	// Cursor that will provide access to the host data we are editing
    private Cursor mCursor;
    
	private int mState;
	private Uri mURI;
	
    @Override
    public void onCreate(Bundle savedValues) {
        super.onCreate(savedValues);
        
        // TODO: update or remove
        // Have the system blur any windows behind this one.
        //getWindow().setFlags(WindowManager.LayoutParams.BLUR_BEHIND_FLAG,
        //        WindowManager.LayoutParams.BLUR_BEHIND_FLAG);
        
        // Apply a tint to any windows behind this one.  Doing a tint this
        // way is more efficient than using a translucent background.  Note
        // that the tint color really should come from a resource.
        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.tintBehind = 0x60000820;
        getWindow().setAttributes(lp);

        this.setContentView(R.layout.host_editor);
        
        // Set up click handlers for text fields and button
        mHostname = (EditText) findViewById(R.id.hostname);        
        mUsername = (EditText) findViewById(R.id.username);
        mPort = (EditText) findViewById(R.id.port);
        
        Button addButton = (Button) findViewById(R.id.add);
        addButton.setOnClickListener(mCommitListener);
        
        Button cancelButton = (Button) findViewById(R.id.cancel);
        cancelButton.setOnClickListener(mCancelListener);
        
        final Intent intent = getIntent();
        
        // Do some setup based on the action being performed.

        final String action = intent.getAction();
        if (Intent.INSERT_ACTION.equals(action)) {
        	mState = STATE_INSERT;
        	mURI = getContentResolver().insert(intent.getData(), null);
        
        	// If we were unable to create a new note, then just finish
        	// this activity.  A RESULT_CANCELED will be sent back to the
        	// original activity if they requested a result.
        	if (mURI == null) {
            	Log.e("Notes", "Failed to insert new note into "
                    + getIntent().getData());
            	finish();
            	return;
        	}
        	
        	// The new entry was created, so assume all will end well and
            // set the result to be returned.
            setResult(RESULT_OK, mURI.toString());
        } else {
        	// Editing is the default state.
       		mState = STATE_EDIT;
        	
        	// Get the URI of the host whose properties we want to edit
            mURI = getIntent().getData();
            
            // If were editing, change the Ok button to be Change instead.
            addButton.setText(R.string.button_change);
        }
        
        // Get a cursor to access the host data
        mCursor = managedQuery(mURI, PROJECTION, null, null);
    }

    @Override
    protected void onResume() {
    	super.onResume();
    	
    	// Initialize the text with the host data
    	if (mCursor != null) {
    		mCursor.first();
    		
    		String hostname = mCursor.getString(HOSTNAME_INDEX);
    		mHostname.setText(hostname);
    		
    		String username = mCursor.getString(USERNAME_INDEX);
    		mUsername.setText(username);
    		
    		String port = mCursor.getString(PORT_INDEX);
    		mPort.setText(port);
    	}
    }

    @Override
    protected void onPause() {
    	super.onPause();
    	
    	// Write the text back into the cursor
    	if (mCursor != null) {
    		String hostname = mHostname.getText().toString();
    		mCursor.updateString(HOSTNAME_INDEX, hostname);
    		
    		String username = mUsername.getText().toString();
    		mCursor.updateString(USERNAME_INDEX, username);
    		
    		String portStr = mPort.getText().toString();
    		int port = Integer.parseInt(portStr);
    		mCursor.updateInt(PORT_INDEX, port);
    		
    		if (isFinishing()
    				&& ((hostname.length() == 0)
    						|| (username.length() == 0)
    						|| (port == 0))) {
    			setResult(RESULT_CANCELED);
    			deleteHost();
    		} else {
    			managedCommitUpdates(mCursor);
    		}
    	}
    }
    
    private final void cancelEdit() {
    	if (mCursor != null) {
    		if (mState == STATE_EDIT) {
    			mCursor.deactivate();
    			mCursor = null;
    		} else if (mState == STATE_INSERT) {
    			deleteHost();
    		}
    	}
    }
    
    private final void deleteHost() {
    	if (mCursor != null) {
    		mHostname.setText("");
    		mUsername.setText("");
    		mPort.setText("");
    		mCursor.deleteRow();
    		mCursor.deactivate();
    		mCursor = null;
    	}
    }
    
    OnClickListener mCommitListener = new OnClickListener() {
    	public void onClick(View v) {
    		// When the user clicks, just finish this activity.
    		// onPause will be called, and we save our data there.
    		finish();
    	}
    };
    
    OnClickListener mCancelListener = new OnClickListener() {
    	public void onClick(View v) {
    		cancelEdit();
    		finish();
    	}
    };
}
