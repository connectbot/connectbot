package org.theb.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Semaphore;

import org.theb.provider.HostDb;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.KeyCharacterMap;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Window;
import android.widget.TextView;

import com.trilead.ssh2.ChannelCondition;
import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ConnectionMonitor;
import com.trilead.ssh2.Session;

public class SecureShell extends Activity {
	private TextView mOutput;
	private ConnectionThread mConn;
	private String mBuffer;
	private KeyCharacterMap mKMap;
	
	static final int PASSWORD_REQUEST = 0;
	
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
	
	// Store the username, hostname, and port from the database.
	private String mHostname;
	private String mUsername;
	private int mPort;
	
	// The toggle for the original thread to release the indeterminate waiting graphic.
	private ProgressDialog progress;
	private boolean mIsWaiting;
	private String mWaitingTitle;
	private String mWaitingMessage;
	
	// Connection lost reason.
	private String mDisconnectReason;
	
	// This is for the password dialog.
	Semaphore sPass;
	String mPassword = null;
	
	Connection conn;
	Session sess;
	InputStream stdin;
	InputStream stderr;
	OutputStream stdout;
	int x;
	int y;
		
	final Handler mHandler = new Handler();
	
	final Runnable mUpdateView = new Runnable() {
		public void run() {
			updateViewInUI();
		}
	};
	
	class ConnectionThread extends Thread {
		String hostname;
		String username;
		int port;
		
		char[][] lines;
		int posy = 0;
		int posx = 0;
		
		public ConnectionThread(String hostname, String username, int port) {
			this.hostname = hostname;
			this.username = username;
			this.port = port;
		}
		
		public void run() {
			conn = new Connection(hostname, port);

			conn.addConnectionMonitor(mConnectionMonitor);
			
            setWaiting(true, "Connection",
            		"Connecting to " + hostname + "...");
			
			Log.d("SSH", "Starting connection attempt...");

	        try {
				conn.connect(new InteractiveHostKeyVerifier());

				setWaiting(true, "Authenticating",
						"Trying to authenticate...");
				
				Log.d("SSH", "Starting authentication...");
				
//				boolean enableKeyboardInteractive = true;
//				boolean enableDSA = true;
//				boolean enableRSA = true;
				
				while (true) {
					/*
					if ((enableDSA || enableRSA ) &&
							mConn.isAuthMethodAvailable(username, "publickey");
							*/
					
					if (conn.isAuthMethodAvailable(username, "password")) {
						Log.d("SSH", "Trying password authentication...");
						setWaiting(true, "Authenticating",
								"Trying to authenticate using password...");
						
						// Set a semaphore that is unset by the returning dialog.
						sPass = new Semaphore(0);
						askPassword();
						
						// Wait for the user to answer.
						sPass.acquire();
						sPass = null;
						if (mPassword == null)
							continue;
						
						boolean res = conn.authenticateWithPassword(username, mPassword);
						if (res == true)
							break;
						
						continue;
					}
					
					throw new IOException("No supported authentication methods available.");
				}
				
				Log.d("SSH", "Opening session...");
				setWaiting(true, "Session", "Requesting shell...");
				
				sess = conn.openSession();
				
		        y = (int)(mOutput.getHeight() / mOutput.getLineHeight());
		        // TODO: figure out how to get the width of monospace font characters.
		        x = y * 3;
		        Log.d("SSH", "Requesting PTY of size " + x + "x" + y);
		        
				sess.requestPTY("dumb", x, y, 0, 0, null);
				
				Log.d("SSH", "Requesting shell...");
				sess.startShell();

				stdout = sess.getStdin();
				stderr = sess.getStderr();
				stdin = sess.getStdout();

				setWaiting(false, null, null);
			} catch (IOException e) {
				Log.e("SSH", e.getMessage());
				setWaiting(false, null, null);
				return;
			} catch (InterruptedException e) {
				// This thread is coming to an end. Let us exit.
				Log.e("SSH", "Connection thread interrupted.");
				return;
			}
			
			byte[] buff = new byte[8192];
			lines = new char[y][];
			
			try {
				while (true) {
					if ((stdin.available() == 0) && (stderr.available() == 0)) {
						int conditions = sess.waitForCondition(
								ChannelCondition.STDOUT_DATA
								| ChannelCondition.STDERR_DATA
								| ChannelCondition.EOF, 2000);
						if ((conditions & ChannelCondition.TIMEOUT) != 0)
							continue;
						if ((conditions & ChannelCondition.EOF) != 0)
							if ((conditions &
									(ChannelCondition.STDERR_DATA
											| ChannelCondition.STDOUT_DATA)) == 0)
								break;
					}
					
					if (stdin.available() > 0) {
						int len = stdin.read(buff);
						addText(buff, len);
					}
					if (stderr.available() > 0) {
						int len = stderr.read(buff);
						addText(buff, len);
					}
				}
			} catch (Exception e) {
				Log.e("SSH", "Got exception reading: " + e.getMessage());
			}
		}
		
		public void addText(byte[] data, int len) {
			for (int i = 0; i < len; i++) {
				char c = (char) (data[i] & 0xff);
			
				if (c == 8) { // Backspace, VERASE
					if (posx < 0)
						continue;
					posx--;
					continue;
				}
				if (c == '\r') {
					posx = 0;
					continue;
				}

				if (c == '\n') {
					posy++;
					if (posy >= y) {
						for (int k = 1; k < y; k++)
							lines[k - 1] = lines[k];
						
						posy--;
						lines[y - 1] = new char[x];
						
						for (int k = 0; k < x; k++)
							lines[y - 1][k] = ' ';
					}
					continue;
				}

				if (c < 32) {
					continue;
				}

				if (posx >= x) {
					posx = 0;
					posy++;
					if (posy >= y) {
						posy--;
						
						for (int k = 1; k < y; k++)
							lines[k - 1] = lines[k];
						lines[y - 1] = new char[x];
						for (int k = 0; k < x; k++)
							lines[y - 1][k] = ' ';
					}
				}

				if (lines[posy] == null) {
					lines[posy] = new char[x];
					for (int k = 0; k < x; k++)
						lines[posy][k] = ' ';
				}

				lines[posy][posx] = c;
				posx++;
			}
			
			StringBuffer sb = new StringBuffer(x * y);
			
			for (int i = 0; i < lines.length; i++) {
				if (i != 0)
					sb.append('\n');
				
				if (lines[i] != null)
					sb.append(lines[i]);
			}
			
			mBuffer = sb.toString();
			mHandler.post(mUpdateView);
		}
	}
	
    @Override
    public void onCreate(Bundle savedValues) {
        super.onCreate(savedValues);
        
        requestWindowFeature(Window.FEATURE_PROGRESS);
        setContentView(R.layout.secure_shell);
        mOutput  = (TextView) findViewById(R.id.output);
        
        Log.d("SSH", "using URI " + getIntent().getData().toString());
        
        mCursor = managedQuery(getIntent().getData(), PROJECTION, null, null);
        mCursor.first();
        
        mHostname = mCursor.getString(HOSTNAME_INDEX);
        mUsername = mCursor.getString(USERNAME_INDEX);
        mPort = mCursor.getInt(PORT_INDEX);
        
        String title = "SSH: " + mUsername + "@" + mHostname;
        if (mPort != 22)
        	title += Integer.toString(mPort);
        
        this.setTitle(title);
        
        mConn = new ConnectionThread(mHostname, mUsername, mPort);
        
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

	public String askPassword() {
    	Intent intent = new Intent(this, PasswordDialog.class);
    	this.startSubActivity(intent, PASSWORD_REQUEST);
    	return null;
	}

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
            String data, Bundle extras)
	{
	    if (requestCode == PASSWORD_REQUEST) {
	
	        // If the request was cancelled, then we didn't get anything.
	        if (resultCode == RESULT_CANCELED)
				mPassword = "";
			else
	            mPassword = data;
	        
            sPass.release();
	    }
	}
    
	@Override
    public void onDestroy() {
    	super.onDestroy();
    	
    	if (sess != null) {
    		sess.close();
    		sess = null;
    	}
    	
    	if (conn != null) {
    		conn.close();
    		conn = null;
    	}
    	
    	finish();
    }
    
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent msg) {
    	if (stdout != null) {
	    	int c = mKMap.get(keyCode, msg.getMetaState());
	    	try {
				stdout.write(c);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	}
    	
    	return super.onKeyDown(keyCode, msg);
    }
    
    public void updateViewInUI() {
    	mOutput.setText(mBuffer);
    }

    final Runnable mDisconnectAlert = new Runnable() {
    	public void run() {
			AlertDialog d = AlertDialog.show(SecureShell.this,
					"Connection Lost", mDisconnectReason, "Ok", false);
			d.show();
			// TODO: Return to previous activity if connection fails.
	    }
    };
    
    final ConnectionMonitor mConnectionMonitor = new ConnectionMonitor() {
    	public void connectionLost(Throwable reason) {
    		Log.d("SSH", "Connection ended.");
    		mDisconnectReason = reason.getMessage();
    		mHandler.post(mDisconnectAlert);
    	}
    };
}
