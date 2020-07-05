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

package org.connectbot;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;

import org.connectbot.bean.PubkeyBean;
import org.connectbot.util.EntropyDialog;
import org.connectbot.util.EntropyView;
import org.connectbot.util.OnEntropyGatheredListener;
import org.connectbot.util.OnKeyGeneratedListener;
import org.connectbot.util.PubkeyDatabase;
import org.connectbot.util.PubkeyUtils;

import com.trilead.ssh2.crypto.keys.Ed25519Provider;
import com.trilead.ssh2.signature.ECDSASHA2Verify;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.graphics.PorterDuff;
import android.os.Bundle;
import androidx.annotation.VisibleForTesting;
import androidx.appcompat.app.AppCompatActivity;
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
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

public class GeneratePubkeyActivity extends AppCompatActivity implements OnEntropyGatheredListener,
		OnKeyGeneratedListener {
	static {
		// Since this class deals with EdDSA keys, we need to make sure this is available.
		Ed25519Provider.insertIfNeeded();
	}

	public final static String TAG = "CB.GeneratePubkeyAct";

	private final static int[] ECDSA_SIZES = ECDSASHA2Verify.getCurveSizes();

	private LayoutInflater inflater = null;

	private EditText nickname;
	private SeekBar bitsSlider;
	private EditText bitsText;
	private CheckBox unlockAtStartup;
	private CheckBox confirmUse;
	private Button save;
	private ProgressDialog progress;

	private EditText password1, password2;

	private KeyType keyType;

	private int bits;

	private byte[] entropy;

	private enum KeyType {
		RSA(PubkeyDatabase.KEY_TYPE_RSA, 1024, 16384, 2048),
		DSA(PubkeyDatabase.KEY_TYPE_DSA, 1024, 1024, 1024),
		EC(PubkeyDatabase.KEY_TYPE_EC, ECDSA_SIZES[0], ECDSA_SIZES[ECDSA_SIZES.length - 1],
				ECDSA_SIZES[0]),
		ED25519(PubkeyDatabase.KEY_TYPE_ED25519, 256, 256, 256);

		public final String name;
		public final int minimumBits;
		public final int maximumBits;
		public final int defaultBits;

		KeyType(String name, int minimumBits, int maximumBits, int defaultBits) {
			this.name = name;
			this.minimumBits = minimumBits;
			this.maximumBits = maximumBits;
			this.defaultBits = defaultBits;
		}
	}

	private OnKeyGeneratedListener listener;

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);

		setContentView(R.layout.act_generatepubkey);

		nickname = findViewById(R.id.nickname);

		RadioGroup keyTypeGroup = findViewById(R.id.key_type);

		bitsText = findViewById(R.id.bits);
		bitsSlider = findViewById(R.id.bits_slider);

		password1 = findViewById(R.id.password1);
		password2 = findViewById(R.id.password2);

		unlockAtStartup = findViewById(R.id.unlock_at_startup);

		confirmUse = findViewById(R.id.confirm_use);

		save = findViewById(R.id.save);

		inflater = LayoutInflater.from(this);

		nickname.addTextChangedListener(textChecker);
		password1.addTextChangedListener(textChecker);
		password2.addTextChangedListener(textChecker);

		setKeyType(KeyType.RSA);

		// TODO add BC to provide EC for devices that don't have it.
		if (Security.getProviders("KeyPairGenerator.EC") == null) {
			findViewById(R.id.ec).setEnabled(false);
		}

		keyTypeGroup.setOnCheckedChangeListener(new OnCheckedChangeListener() {

			@Override
			public void onCheckedChanged(RadioGroup group, int checkedId) {
				if (checkedId == R.id.rsa) {
					setKeyType(KeyType.RSA);
				} else if (checkedId == R.id.dsa) {
					setKeyType(KeyType.DSA);
				} else if (checkedId == R.id.ec) {
					setKeyType(KeyType.EC);
				} else if (checkedId == R.id.ed25519) {
					setKeyType(KeyType.ED25519);
				}
			}
		});

		bitsSlider.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			@Override
			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromTouch) {
				setBits(keyType.minimumBits + progress);
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
				// We don't care about the start.
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
				setBits(bits);
			}
		});

		bitsText.setOnFocusChangeListener(new OnFocusChangeListener() {
			@Override
			public void onFocusChange(View v, boolean hasFocus) {
				if (!hasFocus) {
					int newBits;
					try {
						newBits = Integer.parseInt(bitsText.getText().toString());
					} catch (NumberFormatException nfe) {
						newBits = keyType.defaultBits;
					}
					setBits(newBits);
				}
			}
		});

		save.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View view) {
				GeneratePubkeyActivity.this.save.setEnabled(false);

				GeneratePubkeyActivity.this.startEntropyGather();
			}
		});

	}

	private void setKeyType(KeyType newKeyType) {
		keyType = newKeyType;
		resetBitDefaults();

		switch (newKeyType) {
		case RSA:
		case EC:
			setAllowBitStrengthChange(true);
			break;
		case DSA:
		case ED25519:
			setAllowBitStrengthChange(false);
			break;
		default:
			throw new AssertionError("Impossible key type encountered");
		}
	}

	private void setAllowBitStrengthChange(boolean enabled) {
		bitsSlider.setEnabled(enabled);
		bitsText.setEnabled(enabled);
	}

	private void resetBitDefaults() {
		bitsSlider.setMax(keyType.maximumBits - keyType.minimumBits);
		setBits(keyType.defaultBits);
	}

	private void setBits(int newBits) {
		if (newBits < keyType.minimumBits || newBits > keyType.maximumBits) {
			newBits = keyType.defaultBits;
		}

		if (keyType == KeyType.EC) {
			bits = getClosestFieldSize(newBits);
		} else {
			// Stay evenly divisible by 8 because it looks nicer to have
			// 2048 than 2043 bits.
			bits = newBits - (newBits % 8);
		}

		bitsSlider.setProgress(newBits - keyType.minimumBits);
		bitsText.setText(String.valueOf(bits));
	}

	private void checkEntries() {
		boolean allowSave = true;

		if (!password1.getText().toString().equals(password2.getText().toString()))
			allowSave = false;

		if (nickname.getText().length() == 0)
			allowSave = false;

		if (allowSave) {
			save.getBackground().setColorFilter(getResources().getColor(R.color.accent), PorterDuff.Mode.SRC_IN);
		} else {
			save.getBackground().setColorFilter(null);
		}

		save.setEnabled(allowSave);
	}

	private void startEntropyGather() {
		@SuppressLint("InflateParams")  // Dialogs do not have a parent view.
		final View entropyView = inflater.inflate(R.layout.dia_gatherentropy, null, false);

		((EntropyView) entropyView.findViewById(R.id.entropy)).addOnEntropyGatheredListener(this);
		Dialog entropyDialog = new EntropyDialog(this, entropyView);
		entropyDialog.show();
	}

	@VisibleForTesting
	void setListener(OnKeyGeneratedListener listener) {
		this.listener = listener;
	}

	@Override
	public void onEntropyGathered(byte[] entropy) {
		// For some reason the entropy dialog was aborted, exit activity
		if (entropy == null) {
			finish();
			return;
		}

		this.entropy = entropy.clone();

		int numSetBits = 0;
		for (int i = 0; i < EntropyView.SHA1_MAX_BYTES; i++)
			numSetBits += measureNumberOfSetBits(this.entropy[i]);

		double proportionOfBitsSet = numSetBits / (double) (8 * EntropyView.SHA1_MAX_BYTES);
		Log.d(TAG, "Entropy gathered; population of ones is " +
				(int) (100.0 * proportionOfBitsSet) + "%");

		startKeyGen();
	}

	private static class KeyGeneratorRunnable implements Runnable {
		private final String keyType;
		private final int numBits;
		private final byte[] entropy;
		private final OnKeyGeneratedListener listener;

		KeyGeneratorRunnable(String keyType, int numBits, byte[] entropy,
				OnKeyGeneratedListener listener) {
			this.keyType = keyType;
			this.numBits = numBits;
			this.entropy = entropy;
			this.listener = listener;
		}

		@Override
		public void run() {
			SecureRandom random = new SecureRandom();

			// Work around JVM bug
			random.nextInt();
			random.setSeed(entropy);

			try {
				KeyPairGenerator keyPairGen = KeyPairGenerator.getInstance(keyType);

				keyPairGen.initialize(numBits, random);

				listener.onGenerationSuccess(keyPairGen.generateKeyPair());
			} catch (Exception e) {
				listener.onGenerationError(e);
			}
		}
	}

	private void startKeyGen() {
		progress = new ProgressDialog(GeneratePubkeyActivity.this);
		progress.setMessage(GeneratePubkeyActivity.this.getResources().getText(R.string.pubkey_generating));
		progress.setIndeterminate(true);
		progress.setCancelable(false);
		progress.show();

		Log.d(TAG, "Starting generation of " + keyType + " of strength " + bits);
		KeyGeneratorRunnable keyGen = new KeyGeneratorRunnable(keyType.name, bits, entropy, this);
		Thread keyGenThread = new Thread(keyGen);
		keyGenThread.setName("KeyGen " + keyType + " " + bits);
		keyGenThread.start();
	}

	@Override
	public void onGenerationSuccess(KeyPair pair) {
		try {
			boolean encrypted = false;

			PrivateKey priv = pair.getPrivate();
			PublicKey pub = pair.getPublic();

			String secret = password1.getText().toString();
			if (secret.length() > 0)
				encrypted = true;

			//Log.d(TAG, "private: " + PubkeyUtils.formatKey(priv));
			Log.d(TAG, "public: " + PubkeyUtils.formatKey(pub));

			PubkeyBean pubkey = new PubkeyBean();
			pubkey.setNickname(nickname.getText().toString());
			pubkey.setType(keyType.name);
			pubkey.setPrivateKey(PubkeyUtils.getEncodedPrivate(priv, secret));
			pubkey.setPublicKey(pub.getEncoded());
			pubkey.setEncrypted(encrypted);
			pubkey.setStartup(unlockAtStartup.isChecked());
			pubkey.setConfirmUse(confirmUse.isChecked());

			PubkeyDatabase pubkeydb = PubkeyDatabase.get(GeneratePubkeyActivity.this);
			pubkeydb.savePubkey(pubkey);
		} catch (Exception e) {
			Log.e(TAG, "Could not generate key pair");
			e.printStackTrace();
		}

		// Chain this up for testing purposes. This is used to implement an IdlingResource
		if (listener != null) {
			listener.onGenerationSuccess(pair);
		}

		dismissActivity();
	}

	@Override
	public void onGenerationError(Exception e) {
		Log.e(TAG, "Could not generate key pair");
		e.printStackTrace();

		// Chain this up for testing purposes. This is used to implement an IdlingResource
		if (listener != null) {
			listener.onGenerationError(e);
		}

		dismissActivity();
	}

	private void dismissActivity() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				progress.dismiss();
				GeneratePubkeyActivity.this.finish();
			}
		});
	}

	final private TextWatcher textChecker = new TextWatcher() {
		@Override
		public void afterTextChanged(Editable s) {}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count,
				int after) {}

		@Override
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

	private int getClosestFieldSize(int bits) {
		int outBits = ECDSA_SIZES[0];
		int distance = Math.abs(bits - outBits);

		for (int i = 1; i < ECDSA_SIZES.length; i++) {
			int thisDistance = Math.abs(bits - ECDSA_SIZES[i]);
			if (thisDistance < distance) {
				distance = thisDistance;
				outBits = ECDSA_SIZES[i];
			}
		}
		return outBits;
	}
}
