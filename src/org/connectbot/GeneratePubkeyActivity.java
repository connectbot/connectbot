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

package org.connectbot;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

import org.connectbot.bean.PubkeyBean;
import org.connectbot.util.EntropyDialog;
import org.connectbot.util.EntropyView;
import org.connectbot.util.OnEntropyGatheredListener;
import org.connectbot.util.PubkeyDatabase;
import org.connectbot.util.PubkeyUtils;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class GeneratePubkeyActivity extends Activity implements OnEntropyGatheredListener {
	public final static String TAG = "ConnectBot.GeneratePubkeyActivity";

	final static int DEFAULT_BITS = 1024;

	private LayoutInflater inflater = null;

	private EditText nickname;
	private RadioGroup keyTypeGroup;
	private SeekBar bitsSlider;
	private EditText bitsText;
	private CheckBox unlockAtStartup;
	private Button save;
	private Dialog entropyDialog;
	private ProgressDialog progress;

	private EditText password1, password2;

	private String keyType = PubkeyDatabase.KEY_TYPE_RSA;
	private int minBits = 768;
	private int bits = DEFAULT_BITS;

	private byte[] entropy;

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		setContentView(R.layout.act_generatepubkey);

		nickname = (EditText) findViewById(R.id.nickname);

		keyTypeGroup = (RadioGroup) findViewById(R.id.key_type);

		bitsText = (EditText) findViewById(R.id.bits);
		bitsSlider = (SeekBar) findViewById(R.id.bits_slider);

		password1 = (EditText) findViewById(R.id.password1);
		password2 = (EditText) findViewById(R.id.password2);

		unlockAtStartup = (CheckBox) findViewById(R.id.unlock_at_startup);

		save = (Button) findViewById(R.id.save);

		inflater = (LayoutInflater)getSystemService(Context.LAYOUT_INFLATER_SERVICE);

		nickname.addTextChangedListener(textChecker);
		password1.addTextChangedListener(textChecker);
		password2.addTextChangedListener(textChecker);

		keyTypeGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			public void onCheckedChanged(RadioGroup group, int checkedId) {
				if (checkedId == R.id.rsa) {
					minBits = 768;

					bitsSlider.setEnabled(true);
					bitsSlider.setProgress(DEFAULT_BITS - minBits);

					bitsText.setText(String.valueOf(DEFAULT_BITS));
					bitsText.setEnabled(true);

					keyType = PubkeyDatabase.KEY_TYPE_RSA;
				} else if (checkedId == R.id.dsa) {
					// DSA keys can only be 1024 bits

					bitsSlider.setEnabled(false);
					bitsSlider.setProgress(DEFAULT_BITS - minBits);

					bitsText.setText(String.valueOf(DEFAULT_BITS));
					bitsText.setEnabled(false);

					keyType = PubkeyDatabase.KEY_TYPE_DSA;
				}
			}
		});

		bitsSlider.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromTouch) {
				// Stay evenly divisible by 8 because it looks nicer to have
				// 2048 than 2043 bits.

				int leftover = progress % 8;
				int ourProgress = progress;

				if (leftover > 0)
					ourProgress += 8 - leftover;

				bits = minBits + ourProgress;
				bitsText.setText(String.valueOf(bits));
			}

			public void onStartTrackingTouch(SeekBar seekBar) {
				// We don't care about the start.
			}

			public void onStopTrackingTouch(SeekBar seekBar) {
				// We don't care about the stop.
			}
		});

		bitsText.setOnFocusChangeListener(new OnFocusChangeListener() {
			public void onFocusChange(View v, boolean hasFocus) {
				if (!hasFocus) {
					try {
						bits = Integer.parseInt(bitsText.getText().toString());
						if (bits < minBits) {
							bits = minBits;
							bitsText.setText(String.valueOf(bits));
						}
					} catch (NumberFormatException nfe) {
						bits = DEFAULT_BITS;
						bitsText.setText(String.valueOf(bits));
					}

					bitsSlider.setProgress(bits - minBits);
				}
			}
		});

		save.setOnClickListener(new OnClickListener() {
			public void onClick(View view) {
				GeneratePubkeyActivity.this.save.setEnabled(false);

				GeneratePubkeyActivity.this.startEntropyGather();
			}
		});

	}

	private void checkEntries() {
		boolean allowSave = true;

		if (!password1.getText().toString().equals(password2.getText().toString()))
			allowSave = false;

		if (nickname.getText().length() == 0)
			allowSave = false;

		save.setEnabled(allowSave);
	}

	private void startEntropyGather() {
		final View entropyView = inflater.inflate(R.layout.dia_gatherentropy, null, false);
		((EntropyView)entropyView.findViewById(R.id.entropy)).addOnEntropyGatheredListener(GeneratePubkeyActivity.this);
		entropyDialog = new EntropyDialog(GeneratePubkeyActivity.this, entropyView);
		entropyDialog.show();
	}

	public void onEntropyGathered(byte[] entropy) {
		// For some reason the entropy dialog was aborted, exit activity
		if (entropy == null) {
			finish();
			return;
		}

		this.entropy = entropy.clone();

		int numSetBits = 0;
		for (int i = 0; i < 20; i++)
			numSetBits += measureNumberOfSetBits(this.entropy[i]);

		Log.d(TAG, "Entropy distribution=" + (int)(100.0 * numSetBits / 160.0) + "%");

		Log.d(TAG, "entropy gathered; attemping to generate key...");
		startKeyGen();
	}

	private void startKeyGen() {
		progress = new ProgressDialog(GeneratePubkeyActivity.this);
		progress.setMessage(GeneratePubkeyActivity.this.getResources().getText(R.string.pubkey_generating));
		progress.setIndeterminate(true);
		progress.setCancelable(false);
		progress.show();

		Thread keyGenThread = new Thread(mKeyGen);
		keyGenThread.setName("KeyGen");
		keyGenThread.start();
	}

	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			progress.dismiss();
			GeneratePubkeyActivity.this.finish();
		}
	};

	final private Runnable mKeyGen = new Runnable() {
		public void run() {
			try {
				boolean encrypted = false;

				SecureRandom random = SecureRandom.getInstance("SHA1PRNG");

				random.setSeed(entropy);

				KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance(keyType);

				keyPairGen.initialize(bits, random);

				KeyPair pair = keyPairGen.generateKeyPair();
				PrivateKey priv = pair.getPrivate();
				PublicKey pub = pair.getPublic();

				String secret = password1.getText().toString();
				if (secret.length() > 0)
					encrypted = true;

				Log.d(TAG, "private: " + PubkeyUtils.formatKey(priv));
				Log.d(TAG, "public: " + PubkeyUtils.formatKey(pub));

				PubkeyBean pubkey = new PubkeyBean();
				pubkey.setNickname(nickname.getText().toString());
				pubkey.setType(keyType);
				pubkey.setPrivateKey(PubkeyUtils.getEncodedPrivate(priv, secret));
				pubkey.setPublicKey(PubkeyUtils.getEncodedPublic(pub));
				pubkey.setEncrypted(encrypted);
				pubkey.setStartup(unlockAtStartup.isChecked());

				PubkeyDatabase pubkeydb = new PubkeyDatabase(GeneratePubkeyActivity.this);
				pubkeydb.savePubkey(pubkey);
				pubkeydb.close();
			} catch (Exception e) {
				Log.e(TAG, "Could not generate key pair");

				e.printStackTrace();
			}

			handler.sendEmptyMessage(0);
		}

	};

	final private TextWatcher textChecker = new TextWatcher() {
		public void afterTextChanged(Editable s) {}

		public void beforeTextChanged(CharSequence s, int start, int count,
				int after) {}

		public void onTextChanged(CharSequence s, int start, int before,
				int count) {
			checkEntries();
		}
	};

	private int measureNumberOfSetBits(byte b) {
		int numSetBits = 0;

		for (int i = 0; i < 8; i++) {
			if ((b & 1) == 1)
				numSetBits++;
			b >>= 1;
		}

		return numSetBits;
	}
}
