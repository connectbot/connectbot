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

package org.connectbot.service;

import java.util.ArrayDeque;

import org.connectbot.AgentActivity;
import org.connectbot.R;
import org.connectbot.util.AgentRequest;
import org.openintents.ssh.authentication.ISshAuthenticationService;
import org.openintents.ssh.authentication.SshAuthenticationApi;
import org.openintents.ssh.authentication.SshAuthenticationApiError;
import org.openintents.ssh.authentication.SshAuthenticationConnection;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.Nullable;

public class AgentManager extends Service {
	private static final String TAG = "CB.AgentManager";

	public static int AGENT_REQUEST_CODE = 1729;

	public static final String AGENT_PENDING_INTENT = "pendingIntent";

	private final ArrayDeque<AgentRequest> mPendingIntentsStack = new ArrayDeque<>();

	private AgentRequest popPendingIntentRequest() {
		synchronized (mPendingIntentsStack) {
			return mPendingIntentsStack.pop();
		}
	}

	private void pushPendingIntentRequest(AgentRequest request) {
		synchronized (mPendingIntentsStack) {
			mPendingIntentsStack.push(request);
		}
	}

	public void abortPendingIntent() {
		popPendingIntentRequest();
	}

	private void cancelRequest(AgentRequest request) {
		request.getResultCallback().onAgentRequestCancel();
	}

	private void onRequestError(AgentRequest request, String errorMessage) {
		request.getResultCallback().onAgentRequestError(errorMessage);
	}

	public class AgentBinder extends Binder {
		public AgentManager getService() {
			return AgentManager.this;
		}
	}

	private final IBinder mAgentBinder = new AgentBinder();

	@Nullable
	@Override
	public IBinder onBind(Intent intent) {
		return mAgentBinder;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		/*
		 * The lifecycle of this service is bound to the lifecycle of TerminalManager, since
		 * authentication might need to occur in the background if connectivity is temporarily
		 * lost, so this service needs to run as long as there are TerminalBridges active in
		 * TerminalManager
		 */
		return START_STICKY;
	}

	public void execute(final AgentRequest request) {
		final SshAuthenticationConnection agentConnection =
				new SshAuthenticationConnection(getApplicationContext(),
						request.getTargetPackage());

		agentConnection.connect(new SshAuthenticationConnection.OnBound() {
			@Override
			public void onBound(ISshAuthenticationService sshAgent) {
				executeInternal(sshAgent, request);
				agentConnection.disconnect();
			}

			@Override
			public void onError() {
			}
		});
	}

	private void executeInternal(ISshAuthenticationService sshAgent,
			final AgentRequest request) {
		SshAuthenticationApi agentApi = new SshAuthenticationApi(getApplicationContext(), sshAgent);

		agentApi.executeApiAsync(request.getRequest(),
				new SshAuthenticationApi.ISshAgentCallback() {
					@Override
					public void onReturn(Intent response) {
						processResponse(response, request);
					}
				});
	}

	private void processResponse(Intent response, AgentRequest request) {
		int resultCode = response.getIntExtra(SshAuthenticationApi.EXTRA_RESULT_CODE,
				SshAuthenticationApi.RESULT_CODE_ERROR);

		switch (resultCode) {
		case SshAuthenticationApi.RESULT_CODE_SUCCESS:
			sendSuccessResult(response, request);
			return;

		case SshAuthenticationApi.RESULT_CODE_ERROR:
			SshAuthenticationApiError error = response
					.getParcelableExtra(SshAuthenticationApi.EXTRA_ERROR);

			sendRemoteErrorResult(error, request);
			return;

		case SshAuthenticationApi.RESULT_CODE_USER_INTERACTION_REQUIRED:
			executePendingIntent(response, request);
			return;

		default:
			onRequestError(request, getString(R.string.agent_unknown_result_code));
		}
	}

	private void sendSuccessResult(Intent result, AgentRequest request) {
		request.getResultCallback().onAgentRequestSuccess(result);
	}

	private void sendRemoteErrorResult(SshAuthenticationApiError error, AgentRequest request) {
		request.getResultCallback().onAgentRequestRemoteError(error);
	}

	private void executePendingIntent(Intent response, AgentRequest request) {
		PendingIntent pendingIntent = response
				.getParcelableExtra(SshAuthenticationApi.EXTRA_PENDING_INTENT);

		// push request onto a stack so we know which request to drop when cancelled
		pushPendingIntentRequest(request);

		Intent intent = new Intent(this, AgentActivity.class);
		intent.putExtra(AGENT_PENDING_INTENT, pendingIntent);
		startActivity(intent);
	}


	public void processPendingIntentResult(int resultCode, Intent result) {
		// get the request belonging to this result
		AgentRequest request = popPendingIntentRequest();

		if (resultCode == Activity.RESULT_CANCELED) {
			cancelRequest(request);
			return;
		}

		if (result != null) {
			request.setRequest(result);

			// execute received Intent again for result
			execute(request);
		} else {
			onRequestError(request, getString(R.string.agent_pending_intent_result_null));
		}
	}
}

