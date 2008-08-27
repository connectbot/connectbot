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

import java.io.IOException;
import java.io.OutputStream;

import org.theb.ssh.R;
import org.theb.provider.HostDb;

import com.trilead.ssh2.ConnectionMonitor;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.view.KeyCharacterMap;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;

public class SecureShell extends Activity implements FeedbackUI, ConnectionMonitor {
	private ConnectionThread mConn;
	
	// Activities we support.
	static final int PASSWORD_REQUEST = 0;
	
	// Database projection indices.
	private static final int HOSTNAME_INDEX = 1;
	private static final int USERNAME_INDEX = 2;
	private static final int PORT_INDEX = 3;
	
	private static final String[] PROJECTION = new String[] {
		HostDb.Hosts._ID, // 0
		HostDb.Hosts.HOSTNAME, // 1
		HostDb.Hosts.USERNAME, // 2
		HostDb.Hosts.PORT, // 3
	};
	
	private Cursor mCursor;
	
	// Map to convert from keyboard presses to actual characters.
	private KeyCharacterMap mKMap;

	// Terminal window
	private Terminal mTerminal;
	
	// We change the meta state when the user presses the center button.
	private boolean mMetaState = false;
	
	// Store the username, hostname, and port from the database.
	private String mHostname;
	private String mUsername;
	private int mPort;
	
	// The toggle for the original thread to release the indeterminate waiting graphic.
	private ProgressDialog progress;
	private boolean mIsWaiting;
	private String mWaitingTitle;
	private String mWaitingMessage;

	final Handler mHandler = new Handler();
	
	// Tell the user why we disconnected.
	private String mDisconnectReason;

    @Override
    public void onCreate(Bundle savedValues) {
        super.onCreate(savedValues);
        
        // TODO: enable opengl for testing on real devices
        //requestWindowFeature(Window.FEATURE_OPENGL);
        requestWindowFeature(Window.FEATURE_PROGRESS);
        mTerminal = new JTATerminalView(this);
        
        // TODO: implement scroll bar on right.
        setContentView((View)mTerminal);
        
        Log.d("SSH", "using URI " + getIntent().getData().toString());
        
        mCursor = managedQuery(getIntent().getData(), PROJECTION, null, null);
        mCursor.moveToFirst();
        
        mHostname = mCursor.getString(HOSTNAME_INDEX);
        mUsername = mCursor.getString(USERNAME_INDEX);
        mPort = mCursor.getInt(PORT_INDEX);

        String title = "SSH: " + mUsername + "@" + mHostname;
        if (mPort != 22)
        	title += Integer.toString(mPort);
        
        this.setTitle(title);
        
        mConn = new TrileadConnectionThread(this, mTerminal, mHostname, mUsername, mPort);
        
        mKMap = KeyCharacterMap.load(KeyCharacterMap.BUILT_IN_KEYBOARD);

        Log.d("SSH", "Starting new ConnectionThread");
        mConn.start();
    }
    
    public void setWaiting(boolean isWaiting, String title, String message) {
    	mIsWaiting = isWaiting;
    	mWaitingTitle = title;
    	mWaitingMessage = message;
    	mHandler.post(mUpdateWaiting);
    }
    
	final Runnable mUpdateWaiting = new Runnable() {
		public void run() {
	    	if (mIsWaiting) {
	    		if (progress == null)
					progress = ProgressDialog.show(SecureShell.this, mWaitingTitle, mWaitingMessage, true, false);
				else {
	    			progress.setTitle(mWaitingTitle);
	    			progress.setMessage(mWaitingMessage);
	    		}
	    	} else {
	    		if (progress != null) {
	    			progress.dismiss();
	    			progress = null;
	    		}
	    	}
		}
	};

	public void askPassword() {
    	Intent intent = new Intent(this, PasswordDialog.class);
    	this.startActivityForResult(intent, PASSWORD_REQUEST);
	}

    @Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == PASSWORD_REQUEST) {
			mConn.setPassword(data.getStringExtra(Intent.EXTRA_TEXT));
		}
	}
    
	@Override
    public void onDestroy() {
    	super.onDestroy();

    	mConn.finish();
    	mConn = null;
    	
    	finish();
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent msg) {
    	final OutputStream out = mConn.getWriter();
    	if (out != null) {
	    	try {
	    		if (mKMap.isPrintingKey(keyCode)
	    				|| keyCode == KeyEvent.KEYCODE_SPACE) {
			    	int c = mKMap.get(keyCode, msg.getMetaState());
			    	if (mMetaState) {
			    		// Support CTRL-A through CTRL-Z
			    		if (c >= 0x61 && c <= 0x79)
			    			c -= 0x60;
			    		else if (c >= 0x40 && c <= 0x59)
			    			c -= 0x39;
			    		mMetaState = false;
			    	}
					out.write(c);
	    		} else	if (keyCode == KeyEvent.KEYCODE_DEL) {
					out.write(0x08); // CTRL-H
	    		} else if (keyCode == KeyEvent.KEYCODE_ENTER
	    				|| keyCode == KeyEvent.KEYCODE_DPAD_LEFT
	    				|| keyCode == KeyEvent.KEYCODE_DPAD_UP
	    				|| keyCode == KeyEvent.KEYCODE_DPAD_DOWN
	    				|| keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
	    			byte[] output = mTerminal.getKeyCode(keyCode, msg.getMetaState());
	    			if (output != null)
	    				out.write(output);
	    		} else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
	    			if (mMetaState) {
	    				out.write(0x1B); // ESCAPE
	    				mMetaState = false;
	    			} else {
	    				mMetaState = true;
	    			}
	    		} else {
	    			// This is not something we handle.
	    			return super.onKeyDown(keyCode, msg);
	    		}
				return true;
			} catch (IOException e) {
				Log.e("SSH", "Can't write to stdout: "+ e.getMessage());
			}
    	}
		return super.onKeyDown(keyCode, msg);
    }

    final Runnable mDisconnectAlert = new Runnable() {
    	public void run() {
    		if (SecureShell.this.isFinishing())
    			return;
    		
//			AlertDialog d = AlertDialog.show(SecureShell.this,
//					"Connection Lost", mDisconnectReason, "Ok", false);
			new AlertDialog.Builder(SecureShell.this)
					.setIcon(R.drawable.icon)
					.setTitle(R.string.alert_disconnect_msg)
					.setPositiveButton(R.string.button_ok, null)
					.show();
			// TODO: Return to previous activity if connection fails.
	    }
    };
    
	public void connectionLost(Throwable reason) {
		Log.d("SSH", "Connection ended.");
		mDisconnectReason = reason.getMessage();
		mHandler.post(mDisconnectAlert);
	}
}
