/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root, Jeffrey Sharkey
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

import org.connectbot.R;

import android.app.Dialog;
import android.content.Context;
import android.view.View;

public class EntropyDialog extends Dialog implements OnEntropyGatheredListener {

	public EntropyDialog(Context context) {
		super(context);

		this.setContentView(R.layout.dia_gatherentropy);
		this.setTitle(R.string.pubkey_gather_entropy);

		((EntropyView) findViewById(R.id.entropy)).addOnEntropyGatheredListener(this);
	}

	public EntropyDialog(Context context, View view) {
		super(context);

		this.setContentView(view);
		this.setTitle(R.string.pubkey_gather_entropy);

		((EntropyView) findViewById(R.id.entropy)).addOnEntropyGatheredListener(this);
	}

	public void onEntropyGathered(byte[] entropy) {
		this.dismiss();
	}

}
