/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright (C) 2017 Christian Hagau <ach@hagau.se>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.util;

import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

import org.connectbot.R;
import org.connectbot.bean.AgentBean;
import org.connectbot.bean.HostBean;
import org.connectbot.service.AgentManager;
import org.openintents.ssh.authentication.SshAuthenticationApi;
import org.openintents.ssh.authentication.SshAuthenticationApiError;
import org.openintents.ssh.authentication.request.KeySelectionRequest;
import org.openintents.ssh.authentication.request.PublicKeyRequest;
import org.openintents.ssh.authentication.response.KeySelectionResponse;
import org.openintents.ssh.authentication.response.PublicKeyResponse;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;


public class AgentKeySelection implements AgentRequest.AgentRequestResultCallback {
	public static final String TAG = "CB.AgentKeySelection";

	private boolean mKeyIdSelected;

	public interface AgentKeySelectionCallback {
		void onAgentKeySelectionSuccess(AgentBean agentBean);

		void onAgentKeySelectionError(String message);

		void onAgentKeySelectionCancel();
	}

	private AgentManager mAgentManager = null;

	private ServiceConnection mAgentConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mAgentManager = ((AgentManager.AgentBinder) service).getService();
			getKeyId();
		}

		public void onServiceDisconnected(ComponentName className) {
			mAgentManager = null;
		}
	};

	private Context mAppContext;
	private String mAgentName;

	private HostBean mHostBean;
	private AgentBean mAgentBean;

	private AgentKeySelectionCallback mResultCallback;

	public void setResultCallback(AgentKeySelectionCallback callback) {
		mResultCallback = callback;
	}

	public AgentKeySelection(Context appContext, HostBean hostBean, String agentName,
			AgentKeySelectionCallback resultCallback) {
		mAppContext = appContext;
		mAgentName = agentName;
		mResultCallback = resultCallback;

		mHostBean = hostBean;

		mAgentBean = new AgentBean();
		mAgentBean.setPackageName(mAgentName);
	}

	/**
	 * Select a key from an external ssh-agent
	 */
	public void selectKeyFromAgent() {
		mAppContext.bindService(new Intent(mAppContext, AgentManager.class), mAgentConnection,
				Context.BIND_AUTO_CREATE);
	}

	protected void onResult(Intent response) {
		int resultCode = response.getIntExtra(SshAuthenticationApi.EXTRA_RESULT_CODE,
				SshAuthenticationApi.RESULT_CODE_ERROR);
		if (resultCode != SshAuthenticationApi.RESULT_CODE_SUCCESS) {
			finishError(getErrorMessage(response));
		}

		if (!mKeyIdSelected) {
			onKeySelected(new KeySelectionResponse(response));
		} else {
			onPublicKey(new PublicKeyResponse(response));
		}
	}

	private void finish() {
		mAppContext.unbindService(mAgentConnection);
	}

	protected void finishCancel() {
		mResultCallback.onAgentKeySelectionCancel();
		finish();
	}

	protected void finishError(String message) {
		mResultCallback.onAgentKeySelectionError(message);
		finish();
	}

	protected void finishSuccess() {
		mResultCallback.onAgentKeySelectionSuccess(mAgentBean);
		finish();
	}

	private PublicKey decodePublicKey(byte[] encodedPublicKey, int algorithmFlag) {
		PublicKey publicKey;
		try {
			publicKey = PubkeyUtils.decodePublic(encodedPublicKey,
					translateAlgorithm(algorithmFlag));
		} catch (NoSuchAlgorithmException e) {
			Log.e(TAG, "Couldn't translate algorithm", e);
			return null;
		} catch (InvalidKeySpecException e) {
			Log.e(TAG, "Couldn't decode public key", e);
			return null;
		}
		return publicKey;
	}

	private String translateAlgorithm(int algorithm) throws NoSuchAlgorithmException {
		switch (algorithm) {
		case SshAuthenticationApi.RSA:
			return "RSA";
		case SshAuthenticationApi.DSA:
			return "DSA";
		case SshAuthenticationApi.ECDSA:
			return "EC";
		case SshAuthenticationApi.EDDSA:
			return "Ed25519";
		default:
			throw new NoSuchAlgorithmException("Algorithm not supported: " + algorithm);
		}
	}

	private void getKeyId() {
		Intent request = new KeySelectionRequest().toIntent();

		AgentRequest agentRequest = new AgentRequest(request, mAgentName);
		agentRequest.setResultCallback(this);

		mAgentManager.execute(agentRequest);
	}

	private void onKeySelected(KeySelectionResponse response) {
		mAgentBean.setKeyIdentifier(response.getKeyId());
		mAgentBean.setDescription(response.getKeyDescription());
		mKeyIdSelected = true;
		getPublicKey();
	}

	private void getPublicKey() {
		Intent request = new PublicKeyRequest(mAgentBean.getKeyIdentifier()).toIntent();

		AgentRequest agentRequest = new AgentRequest(request, mAgentName);
		agentRequest.setResultCallback(this);

		mAgentManager.execute(agentRequest);
	}

	private void onPublicKey(PublicKeyResponse response) {
		byte[] encodedPublicKey = response.getEncodedPublicKey();
		int algorithm = response.getKeyAlgorithm();

		// try decoding the encoded key to make sure it can be used for authentication later
		PublicKey publicKey = decodePublicKey(encodedPublicKey, algorithm);
		if (publicKey == null) {
			finishError(mAppContext.getString(R.string.agent_error_decoding_key));
			return;
		}

		try {
			mAgentBean.setKeyType(translateAlgorithm(algorithm));
		} catch (NoSuchAlgorithmException e) {
			Log.e(TAG, "Couldn't translate algorithm", e);
			finishError(mAppContext.getString(R.string.agent_key_algorithm_not_supported));
			return;
		}
		mAgentBean.setPublicKey(publicKey.getEncoded());

		finishSuccess();
	}

	private String getErrorMessage(Intent errorResult) {
		SshAuthenticationApiError error = errorResult
				.getParcelableExtra(SshAuthenticationApi.EXTRA_ERROR);
		return error.getMessage();
	}

	@Override
	public void onAgentRequestSuccess(Intent result) {
		onResult(result);
	}

	@Override
	public void onAgentRequestError(String errorMessage) {
		finishError(mAppContext.getString(R.string.agent_internal_error, errorMessage));
	}

	@Override
	public void onAgentRequestRemoteError(SshAuthenticationApiError error) {
		finishError(error.getMessage());
	}

	@Override
	public void onAgentRequestCancel() {
		finishCancel();
	}

}

