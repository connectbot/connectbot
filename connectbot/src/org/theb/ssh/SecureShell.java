package org.theb.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.Semaphore;

import org.theb.provider.HostDb;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.ConnectionMonitor;
import com.trilead.ssh2.Session;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ContentURI;
import android.os.Bundle;
import android.os.Handler;
import android.text.method.KeyCharacterMap;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

public class SecureShell extends Activity {
	private Context mContext;
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
	
	// This is for the password dialog.
	Semaphore sPass;
	String mPassword = null;
	
	Connection conn;
	Session sess;
	InputStream in;
	OutputStream out;
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
			
			Log.d("SSH", "Starting connection attempt...");
			mBuffer =  "Attemping to connect...";
			mHandler.post(mUpdateView);

	        try {
				conn.connect(new InteractiveHostKeyVerifier());

				Log.d("SSH", "Starting authentication...");
				mBuffer =  "Attemping to authenticate...";
				mHandler.post(mUpdateView);
				
				boolean enableKeyboardInteractive = true;
				boolean enableDSA = true;
				boolean enableRSA = true;
				
				while (true) {
					/*
					if ((enableDSA || enableRSA ) &&
							mConn.isAuthMethodAvailable(username, "publickey");
							*/
					
					if (conn.isAuthMethodAvailable(username, "password")) {
						Log.d("SSH", "Trying password authentication...");
						
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
				mBuffer =  "Opening session...";
				mHandler.post(mUpdateView);
				
				sess = conn.openSession();
				
		        y = (int)(mOutput.getHeight() / mOutput.getLineHeight());
		        // TODO: figure out how to get the width of monospace font characters.
		        x = y * 3;
		        Log.d("SSH", "Requesting PTY of size " + x + "x" + y);
		        
				sess.requestPTY("dumb", x, y, 0, 0, null);
				
				Log.d("SSH", "Requesting shell...");
				sess.startShell();

				out = sess.getStdin();
				in = sess.getStdout();

				mBuffer = "Welcome...";
				mHandler.post(mUpdateView);
				
			} catch (IOException e) {
				Log.e("SSH", e.getMessage());
				mConnectionMonitor.connectionLost(e);
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
					int len = in.read(buff);
					if (len == -1)
						return;
					addText(buff, len);
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
        
        mContext = this;
        
        setContentView(R.layout.secure_shell);
        
        Log.d("SSH", "using URI " + getIntent().getData().toString());
        
        mCursor = managedQuery(getIntent().getData(), PROJECTION, null, null);
        mCursor.first();
        
        mOutput  = (TextView) findViewById(R.id.output);
        
        mKMap = KeyCharacterMap.load(KeyCharacterMap.BUILT_IN_KEYBOARD);
                
        mConn = new ConnectionThread(
        		mCursor.getString(HOSTNAME_INDEX),
        		mCursor.getString(USERNAME_INDEX),
        		mCursor.getInt(PORT_INDEX));
        
        Log.d("SSH", "Starting new ConnectionThread");
        mConn.start();
    }
    
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
	        if (resultCode == RESULT_CANCELED) {
	            return;
	
	        // Otherwise, there now should be a password ready for us.
	        } else {
	            mPassword = data;
	            sPass.release();
	        }
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
    	if (out != null) {
	    	int c = mKMap.get(keyCode, msg.getMetaState());
	    	try {
				out.write(c);
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

    final ConnectionMonitor mConnectionMonitor = new ConnectionMonitor() {
    	public void connectionLost(Throwable reason) {
    		Log.d("SSH", "Connection ended.");
    		Dialog d = new Dialog(mContext);
    		d.setTitle("Connection Lost");
    		d.setContentView(R.layout.message_dialog);
    		
    		TextView msg = (TextView) d.findViewById(R.id.message);
    		msg.setText(reason.getMessage());
    		
    		Button b = (Button) d.findViewById(R.id.dismiss);
    		b.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					// TODO Auto-generated method stub
					finish();
				}
    		});
    		d.show();
    		finish();
    	}
    };
}
