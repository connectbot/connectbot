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

import org.connectbot.util.AgentKeySelection.AgentKeySelectionCallback;

import android.os.Bundle;
import android.support.v4.app.Fragment;

public class AgentKeySelectionRetainerFragment extends Fragment {
	public final static String TAG = "CB.AgentKeySelectionRetainerFragment";

	private AgentKeySelection mAgentKeySelection;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRetainInstance(true);
	}

	public void setAgentKeySelection(AgentKeySelection agentKeySelection) {
		mAgentKeySelection = agentKeySelection;
	}

	public void setResultCallback(AgentKeySelectionCallback callback) {
		mAgentKeySelection.setResultCallback(callback);
	}
}
