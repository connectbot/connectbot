package org.connectbot.service;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.connectbot.service.TerminalManager.KeyHolder;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.madgag.ssh.android.authagent.AndroidAuthAgent;
import com.trilead.ssh2.signature.DSAPrivateKey;
import com.trilead.ssh2.signature.DSAPublicKey;
import com.trilead.ssh2.signature.DSASHA1Verify;
import com.trilead.ssh2.signature.DSASignature;
import com.trilead.ssh2.signature.RSAPrivateKey;
import com.trilead.ssh2.signature.RSAPublicKey;
import com.trilead.ssh2.signature.RSASHA1Verify;
import com.trilead.ssh2.signature.RSASignature;

public class AuthAgentService extends Service {
	private static final String TAG = "ConnectBot.AuthAgentService";
	protected TerminalManager manager;
	final Lock lock = new ReentrantLock();
	final Condition managerReady = lock.newCondition();

	private ServiceConnection connection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			Log.d(TAG, "Terminal manager available! Hurrah");
			manager = ((TerminalManager.TerminalBinder) service).getService();
			lock.lock();
			try {
				managerReady.signal();
			} finally {
				lock.unlock();
			}
		}

		public void onServiceDisconnected(ComponentName className) {
			manager = null;
			Log.d(TAG, "Terminal manager gone...");
		}
	};

	@Override
	public IBinder onBind(Intent intent) {
		Log.d(TAG, "onBind() called");
		bindService(new Intent(this, TerminalManager.class), connection, BIND_AUTO_CREATE);
		return mBinder;
	}

	private final AndroidAuthAgent.Stub mBinder = new AndroidAuthAgent.Stub() {

		public Map getIdentities() throws RemoteException {
			Log.d(TAG, "getIdentities() called");
			waitForTerminalManager();
			Log.d(TAG, "getIdentities() manager.loadedKeypairs : " + manager.loadedKeypairs);

			return sshEncodedPubKeysFrom(manager.loadedKeypairs);
		}

		public byte[] sign(byte[] publicKey, byte[] data) throws RemoteException {
			Log.d(TAG, "sign() called");
			waitForTerminalManager();
			Object trileadKey = keyPairFor(publicKey);
			Log.d(TAG, "sign() - signing keypair found : "+trileadKey);

			if (trileadKey == null) {
				return null;
			}

			if (trileadKey instanceof RSAPrivateKey) {
				return sshEncodedSignatureFor(data, (RSAPrivateKey) trileadKey);
			} else if (trileadKey instanceof DSAPrivateKey) {
				return sshEncodedSignatureFor(data, (DSAPrivateKey) trileadKey);
			}
			return null;
		}


		private void waitForTerminalManager() throws RemoteException {
			lock.lock();
			try {
				while (manager == null) {
					Log.d(TAG, "Waiting for TerminalManager...");
					managerReady.await();
				}
			} catch (InterruptedException e) {
				throw new RemoteException();
			} finally {
				lock.unlock();
			}
			Log.d(TAG, "Got TerminalManager : "+manager);
		}

		private Map<String, byte[]> sshEncodedPubKeysFrom(Map<String, KeyHolder> keypairs) {
			Map<String, byte[]> encodedPubKeysByName = new HashMap<String, byte[]>(keypairs.size());

			for (Entry<String, KeyHolder> entry : keypairs.entrySet()) {
				byte[] encodedKey = sshEncodedPubKeyFrom(entry.getValue().trileadKey);
				if (encodedKey != null) {
					encodedPubKeysByName.put(entry.getKey(), encodedKey);
				}
			}
			return encodedPubKeysByName;
		}

		private byte[] sshEncodedPubKeyFrom(Object trileadKey) {
			try {
				if (trileadKey instanceof RSAPrivateKey) {
					RSAPublicKey pubkey = ((RSAPrivateKey) trileadKey).getPublicKey();
					return RSASHA1Verify.encodeSSHRSAPublicKey(pubkey);
				} else if (trileadKey instanceof DSAPrivateKey) {
					DSAPublicKey pubkey = ((DSAPrivateKey) trileadKey).getPublicKey();
					return DSASHA1Verify.encodeSSHDSAPublicKey(pubkey);
				}
			} catch (IOException e) {
				Log.e(TAG, "Couldn't encode " + trileadKey, e);
			}
			return null;
		}

		private byte[] sshEncodedSignatureFor(byte[] data, RSAPrivateKey trileadKey) {
			try {
				RSASignature signature = RSASHA1Verify.generateSignature(data, trileadKey);
				return RSASHA1Verify.encodeSSHRSASignature(signature);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		private byte[] sshEncodedSignatureFor(byte[] data, DSAPrivateKey dsaPrivateKey) {
			DSASignature signature = DSASHA1Verify.generateSignature(data, dsaPrivateKey, new SecureRandom());
			return DSASHA1Verify.encodeSSHDSASignature(signature);
		}

		private Object keyPairFor(byte[] publicKey) {
			String nickname = manager.getKeyNickname(publicKey);

			if (nickname == null) {
				Log.w(TAG, "No key-pair found for public-key.");
				return null;
			}

			// check manager.loadedKeypairs.get(nickname).bean.isConfirmUse() and promptForPubkeyUse(nickname) ?
			return manager.getKey(nickname);
		}

	};
}
