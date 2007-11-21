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

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;

public class PasswordDialog extends Activity implements OnClickListener {
	private EditText mPassword;
	private Button mOK;
	
	@Override
	public void onCreate(Bundle savedValues) {
		super.onCreate(savedValues);
		
		setContentView(R.layout.password_dialog);
		
		mPassword = (EditText) findViewById(R.id.password);
		mOK = (Button) findViewById(R.id.ok);
		mOK.setOnClickListener(this);
	}

	public void onClick(View arg0) {
		setResult(RESULT_OK, mPassword.getText().toString());
		finish();
	}
}
