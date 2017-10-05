/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2017 Jonas Dippel, Michael Perk, Marc Totzke
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

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.CountDownLatch;

import org.connectbot.bean.AgentBean;
import org.connectbot.service.AgentManager;
import org.openintents.ssh.authentication.SshAuthenticationApi;
import org.openintents.ssh.authentication.SshAuthenticationApiError;
import org.openintents.ssh.authentication.request.SigningRequest;

import com.trilead.ssh2.auth.SignatureProxy;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;


public class AgentSignatureProxy extends SignatureProxy
		implements AgentRequest.AgentRequestResultCallback {
	private static final String TAG = "CB.AgentSignatureProxy";

	public static final int RESULT_CODE_CANCELED = -1;
	public static final int RESULT_CODE_SUCCESS = 0;
	public static final int RESULT_CODE_ERROR = 1;
	public static final int RESULT_CODE_REMOTE_ERROR = 2;

	private Context mAppContext;

	private AgentBean mAgentBean;

	private AgentRequest mAgentRequest;

	private byte[] mSignature;

	private SshAuthenticationApiError mRemoteError;

	private String mErrorMessage;

	private int mResultCode;

	private CountDownLatch mResultReadyLatch;

	private AgentManager mAgentManager = null;

	private ServiceConnection mAgentConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			mAgentManager = ((AgentManager.AgentBinder) service).getService();
			mAgentManager.execute(mAgentRequest);
		}

		public void onServiceDisconnected(ComponentName className) {
			mAgentManager = null;
		}
	};

	public AgentSignatureProxy(Context appContext, AgentBean agentBean)
			throws InvalidKeySpecException, NoSuchAlgorithmException {
		super(PubkeyUtils.decodePublic(agentBean.getPublicKey(), agentBean.getKeyType()));
		mAppContext = appContext;
		mAgentBean = agentBean;

		mResultReadyLatch = new CountDownLatch(1);
	}

	@Override
	public byte[] sign(final byte[] challenge, final String hashAlgorithm) throws IOException {
		Intent request = new SigningRequest(challenge, mAgentBean.getKeyIdentifier(),
				translateHashAlgorithm(hashAlgorithm)).toIntent();

		mAgentRequest = new AgentRequest(request, mAgentBean.getPackageName());
		mAgentRequest.setResultCallback(this);

		mAppContext.bindService(new Intent(mAppContext, AgentManager.class), mAgentConnection,
				Context.BIND_AUTO_CREATE);

		try {
			mResultReadyLatch.await();
		} catch (InterruptedException e) {
			throw new IOException("Error signing challenge: interrupted");
		}
		mAppContext.unbindService(mAgentConnection);

		switch (mResultCode) {
		case RESULT_CODE_SUCCESS:
			if (mSignature != null) {
				return mSignature;
			} else {
				throw new IOException("No signature in agent response");
			}

		case RESULT_CODE_CANCELED:
			throw new IOException("Canceled signing challenge");

		case RESULT_CODE_REMOTE_ERROR:
			throw new IOException("Error signing challenge: " + mRemoteError.getMessage());

		case RESULT_CODE_ERROR:
			throw new IOException("Error signing challenge: " + mErrorMessage);

		default:
			throw new IOException("Error signing challenge");
		}
	}

	private int translateHashAlgorithm(String hashAlgorithm) {
		switch (hashAlgorithm) {
		case SignatureProxy.SHA1:
			return SshAuthenticationApi.SHA1;
		case SignatureProxy.SHA256:
			return SshAuthenticationApi.SHA256;
		case SignatureProxy.SHA384:
			return SshAuthenticationApi.SHA384;
		case SignatureProxy.SHA512:
			return SshAuthenticationApi.SHA512;
		default:
			return SshAuthenticationApiError.INVALID_HASH_ALGORITHM;
		}
	}

	private void unlock() {
		mResultReadyLatch.countDown();
	}

	@Override
	public void onAgentRequestSuccess(Intent result) {
		mResultCode = RESULT_CODE_SUCCESS;
		mSignature = result.getByteArrayExtra(SshAuthenticationApi.EXTRA_SIGNATURE);
		unlock();
	}

	@Override
	public void onAgentRequestError(String errorMessage) {
		mResultCode = RESULT_CODE_ERROR;
		mErrorMessage = errorMessage;
		unlock();
	}

	@Override
	public void onAgentRequestRemoteError(SshAuthenticationApiError error) {
		mResultCode = RESULT_CODE_REMOTE_ERROR;
		mRemoteError = error;
		unlock();
	}

	@Override
	public void onAgentRequestCancel() {
		mResultCode = RESULT_CODE_CANCELED;
		unlock();
	}
}

