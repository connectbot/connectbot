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

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.util.concurrent.Semaphore;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

public class Pubkey extends Activity {
	private static SecureRandom mSecRand = null;
	//private static KeyStore mKeyStore = null;
	private Thread kgThread = null;
	
	private static final int GATHER_ENTROPY = 0;
	
	protected Semaphore entropyGathered;
	protected String entropySeed;
	
	protected Button generateButton;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);	
        
        setContentView(R.layout.pubkey);
        
        generateButton = (Button) findViewById(R.id.generate);
        generateButton.setOnClickListener(mGenerateListener);
        
        Button okButton = (Button) findViewById(R.id.ok);
        okButton.setOnClickListener(mCommitListener);
        
        Button cancelButton = (Button) findViewById(R.id.cancel);
        cancelButton.setOnClickListener(mCancelListener);
    }
    
    final Runnable mKeyGen = new Runnable() {    	
		public void run() {
			// TODO Auto-generated method stub
    		Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    		try {
     			mSecRand = SecureRandom.getInstance("SHA1PRNG");
    			
    			entropyGathered = new Semaphore(0);
    			gatherEntropy();
    			entropyGathered.acquire();
    			mSecRand.setSeed(entropySeed.getBytes());
    			entropyGathered = null;
    			
    			KeyPairGenerator keyGen = KeyPairGenerator.getInstance("DSA");
    			keyGen.initialize(512, mSecRand);
    			
    			KeyPair pair = keyGen.generateKeyPair();
    			PrivateKey priv = pair.getPrivate();
    			PublicKey pub = pair.getPublic();
    			
    			byte[] encPriv = priv.getEncoded();
    			byte[] encPub = pub.getEncoded();
    			Log.e("SSH/priv", new String(encPriv));
    			Log.d("SSH/pub", new String(encPub));
    		} catch (Exception ex) {
    			Log.e("SSH/keygen", ex.getMessage());
    		}			
		}
    };
    
    OnClickListener mGenerateListener = new OnClickListener() {
    	public void onClick(View v) {
    		kgThread = new Thread(mKeyGen);
    		kgThread.start();
    	}
    };
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode,
            String data, Bundle extras)
	{
	    if (requestCode == GATHER_ENTROPY) {
	    	entropySeed = data;
	    	entropyGathered.release();
	    }
	}
    
    protected void gatherEntropy() {
    	generateButton.setEnabled(false);
		Intent intent = new Intent(this, TouchEntropy.class);
		startSubActivity(intent, GATHER_ENTROPY);
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
    		//cancelEdit();
    		finish();
    	}
    };
}
