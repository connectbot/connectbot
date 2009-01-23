/*
	ConnectBot: simple, powerful, open-source SSH client for Android
	Copyright (C) 2007-2008 Kenny Root, Jeffrey Sharkey

	This program is free software: you can redistribute it and/or modify
	it under the terms of the GNU General Public License as published by
	the Free Software Foundation, either version 3 of the License, or
	(at your option) any later version.

	This program is distributed in the hope that it will be useful,
	but WITHOUT ANY WARRANTY; without even the implied warranty of
	MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
	GNU General Public License for more details.

	You should have received a copy of the GNU General Public License
	along with this program.  If not, see <http://www.gnu.org/licenses/>.
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
