package org.theb.ssh;

import java.io.IOException;

import com.trilead.ssh2.Connection;
import com.trilead.ssh2.Session;

import android.content.Context;
import android.graphics.Canvas;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;

public class ShellView extends EditText {

	private Connection mConn;
	private Session mSess;
	
	private String mHostname;
	private String mUsername;
	private String mPassword;

	public ShellView(Context context) {
		super(context);
		// TODO Auto-generated constructor stub
		
		mPassword = "OEfmP07-";
	}
	
	@Override
	public void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		
		append("Connecting... ");
		mConn = new Connection(mHostname);
		
        try {
			mConn.connect(new InteractiveHostKeyVerifier());
			append("OK\n");
		} catch (IOException e) {
			append("Failed.\n");
			Log.e("SSH", e.getMessage());
			append("\nWhoops: " + e.getCause().getMessage() + "\n");
		}
			
		boolean enableKeyboardInteractive = true;
		boolean enableDSA = true;
		boolean enableRSA = true;
		
		try {
			while (true) {
				/*
				if ((enableDSA || enableRSA ) &&
						mConn.isAuthMethodAvailable(username, "publickey");
						*/
				
				if (mConn.isAuthMethodAvailable(mUsername, "password")) {
					boolean res = mConn.authenticateWithPassword(mUsername, mPassword);
					if (res == true)
						break;
					
					append("Login failed.\n");
					continue;
				}
				
				throw new IOException("No supported authentication methods available.");
			}
			
			mSess = mConn.openSession();
			append("Logged in as " + mUsername + ".\n");
		} catch (IOException e) {
			Log.e("SSH", e.getMessage());
		}
		append("Exiting\n");
	}

}
