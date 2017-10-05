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

import org.openintents.ssh.authentication.SshAuthenticationApiError;

import android.content.Intent;

public class AgentRequest {

	public interface AgentRequestResultCallback {
		void onAgentRequestSuccess(Intent result);

		void onAgentRequestError(String errorMessage);

		void onAgentRequestRemoteError(SshAuthenticationApiError error);

		void onAgentRequestCancel();
	}

	private AgentRequestResultCallback mResultCallback;

	private String mTargetPackage;

	private Intent mRequest;

	public AgentRequest(Intent request, String targetPackage) {
		mRequest = request;
		mTargetPackage = targetPackage;
	}

	public String getTargetPackage() {
		return mTargetPackage;
	}

	public AgentRequestResultCallback getResultCallback() {
		return mResultCallback;
	}

	public void setResultCallback(AgentRequestResultCallback resultCallback) {
		mResultCallback = resultCallback;
	}

	public Intent getRequest() {
		return mRequest;
	}

	public void setRequest(Intent request) {
		mRequest = request;
	}
}
