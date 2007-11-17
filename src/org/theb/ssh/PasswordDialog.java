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
