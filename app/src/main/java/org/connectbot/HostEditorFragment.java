/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2015 Kenny Root, Jeffrey Sharkey
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

import java.util.ArrayList;
import java.util.Map;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.SwitchCompat;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import org.connectbot.bean.HostBean;
import org.connectbot.transport.SSH;
import org.connectbot.transport.Telnet;
import org.connectbot.transport.TransportFactory;
import org.connectbot.util.HostDatabase;

public class HostEditorFragment extends Fragment {

	private static final String ARG_EXISTING_HOST_ID = "existingHostId";
	private static final String ARG_EXISTING_HOST = "existingHost";
	private static final String ARG_IS_EXPANDED = "isExpanded";
	private static final String ARG_PUBKEY_NAMES = "pubkeyNames";
	private static final String ARG_PUBKEY_VALUES = "pubkeyValues";
	private static final String ARG_QUICKCONNECT_STRING = "quickConnectString";

	private static final int MINIMUM_FONT_SIZE = 8;

	// The host being edited.
	private HostBean mHost;

	// The pubkey lists (names and values). Note that these are declared as ArrayLists rather than
	// Lists because Bundles can only contain ArrayLists, not general Lists.
	private ArrayList<String> mPubkeyNames;
	private ArrayList<String> mPubkeyValues;
	
	// Whether the host is being created for the first time (as opposed to an existing one being
	// edited).
	private boolean mIsCreating;

	// The listener for changes to this host.
	private Listener mListener;

	// Whether the URI parts subsection is expanded.
	private boolean mIsUriEditorExpanded = false;

	// Whether a URI text edit is in progress. When the quick-connect field is being edited, changes
	// automatically propagate to the URI part fields; likewise, when the URI part fields are
	// edited, changes are propagated to the quick-connect field. This boolean safeguards against
	// infinite loops which can be caused by one field changing the other field, which changes the
	// first field, etc.
	private boolean mUriFieldEditInProgress = false;

	// Values for the colors displayed in the color Spinner. These are not necessarily the same as
	// the text in the Spinner because the text is localized while these values are not.
	private TypedArray mColorValues;

	// Likewise, but for SSH auth agent values.
	private TypedArray mSshAuthValues;

	// Likewise, but for DEL key values.
	private TypedArray mDelKeyValues;

	private Spinner mTransportSpinner;
	private TextInputLayout mQuickConnectContainer;
	private EditText mQuickConnectField;
	private ImageButton mExpandCollapseButton;
	private View mUriPartsContainer;
	private View mUsernameContainer;
	private EditText mUsernameField;
	private View mHostnameContainer;
	private EditText mHostnameField;
	private View mPortContainer;
	private EditText mPortField;
	private EditText mNicknameField;
	private Spinner mColorSelector;
	private TextView mFontSizeText;
	private SeekBar mFontSizeSeekBar;
	private Spinner mPubkeySpinner;
	private View mUseSshConfirmationContainer;
	private SwitchCompat mUseSshAuthSwitch;
	private AppCompatCheckBox mSshAuthConfirmationCheckbox;
	private SwitchCompat mCompressionSwitch;
	private SwitchCompat mStartShellSwitch;
	private SwitchCompat mStayConnectedSwitch;
	private SwitchCompat mCloseOnDisconnectSwitch;
	private EditText mPostLoginAutomationField;
	private Spinner mDelKeySpinner;
	private Spinner mEncodingSpinner;

	public static HostEditorFragment newInstance(
			HostBean existingHost, ArrayList<String> pubkeyNames, ArrayList<String> pubkeyValues) {
		HostEditorFragment fragment = new HostEditorFragment();
		Bundle args = new Bundle();
		if (existingHost != null) {
			args.putLong(ARG_EXISTING_HOST_ID, existingHost.getId());
			args.putParcelable(ARG_EXISTING_HOST, existingHost.getValues());
		}
		args.putStringArrayList(ARG_PUBKEY_NAMES, pubkeyNames);
		args.putStringArrayList(ARG_PUBKEY_VALUES, pubkeyValues);
		fragment.setArguments(args);
		return fragment;
	}

	public HostEditorFragment() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Bundle bundle = savedInstanceState == null ? getArguments() : savedInstanceState;

		Parcelable existingHostParcelable = bundle.getParcelable(ARG_EXISTING_HOST);
		mIsCreating = existingHostParcelable == null;
		if (existingHostParcelable != null) {
			mHost = HostBean.fromContentValues((ContentValues) existingHostParcelable);
			mHost.setId(bundle.getLong(ARG_EXISTING_HOST_ID));
		} else {
			mHost = new HostBean();
		}

		mPubkeyNames = bundle.getStringArrayList(ARG_PUBKEY_NAMES);
		mPubkeyValues = bundle.getStringArrayList(ARG_PUBKEY_VALUES);

		mIsUriEditorExpanded = bundle.getBoolean(ARG_IS_EXPANDED);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_host_editor, container, false);

		mTransportSpinner = (Spinner) view.findViewById(R.id.transport_selector);
		String[] transportNames = TransportFactory.getTransportNames();
		ArrayAdapter<String> transportSelection = new ArrayAdapter<>(
				getActivity(), android.R.layout.simple_spinner_item, transportNames);
		transportSelection.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mTransportSpinner.setAdapter(transportSelection);
		for (int i = 0; i < transportNames.length; i++) {
			if (transportNames[i].equals(mHost.getProtocol())) {
				mTransportSpinner.setSelection(i);
				break;
			}
		}
		mTransportSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				String protocol = (String) mTransportSpinner.getSelectedItem();
				if (protocol == null) {
					// During initialization, protocol can be null before the list of dropdown items
					// has been generated. Return early in that case.
					return;
				}

				mHost.setProtocol(protocol);
				mHost.setPort(TransportFactory.getTransport(protocol).getDefaultPort());
				handleHostChange();

				mQuickConnectContainer.setHint(
						TransportFactory.getFormatHint(protocol, getActivity()));

				// Different protocols have different field types, so show only the fields needed.
				if (SSH.getProtocolName().equals(protocol)) {
					mUsernameContainer.setVisibility(View.VISIBLE);
					mHostnameContainer.setVisibility(View.VISIBLE);
					mPortContainer.setVisibility(View.VISIBLE);
					mExpandCollapseButton.setVisibility(View.VISIBLE);
				} else if (Telnet.getProtocolName().equals(protocol)) {
					mUsernameContainer.setVisibility(View.GONE);
					mHostnameContainer.setVisibility(View.VISIBLE);
					mPortContainer.setVisibility(View.VISIBLE);
					mExpandCollapseButton.setVisibility(View.VISIBLE);
				} else {
					// Local protocol has only one field, so no need to show the URI parts
					// container.
					setUriPartsContainerExpanded(false);
					mExpandCollapseButton.setVisibility(View.INVISIBLE);
				}
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});

		mQuickConnectContainer =
				(TextInputLayout) view.findViewById(R.id.quickconnect_field_container);

		mQuickConnectField = (EditText) view.findViewById(R.id.quickconnect_field);
		String oldQuickConnect = savedInstanceState == null ?
				null : savedInstanceState.getString(ARG_QUICKCONNECT_STRING);
		mQuickConnectField.setText(oldQuickConnect == null ? mHost.toString() : oldQuickConnect);
		mQuickConnectField.addTextChangedListener(new TextWatcher() {
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {
			}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {
			}

			@Override
			public void afterTextChanged(Editable s) {
				if (mTransportSpinner.getSelectedItem() == null) {
					// During initialization, protocol can be null before the list of dropdown items
					// has been generated. Return early in that case.
					return;
				}

				if (!mUriFieldEditInProgress) {
					applyQuickConnectString(
							s.toString(), (String) mTransportSpinner.getSelectedItem());

					mUriFieldEditInProgress = true;
					mUsernameField.setText(mHost.getUsername());
					mHostnameField.setText(mHost.getHostname());
					mPortField.setText(Integer.toString(mHost.getPort()));
					mUriFieldEditInProgress = false;
				}
			}
		});

		mExpandCollapseButton = (ImageButton) view.findViewById(R.id.expand_collapse_button);
		mExpandCollapseButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				setUriPartsContainerExpanded(!mIsUriEditorExpanded);
			}
		});

		mUriPartsContainer = view.findViewById(R.id.uri_parts_container);

		mUsernameContainer = view.findViewById(R.id.username_field_container);
		mUsernameField = (EditText) view.findViewById(R.id.username_edit_text);
		mUsernameField.setText(mHost.getUsername());
		mUsernameField.addTextChangedListener(new HostTextFieldWatcher(HostDatabase.FIELD_HOST_USERNAME));

		mHostnameContainer = view.findViewById(R.id.hostname_field_container);
		mHostnameField = (EditText) view.findViewById(R.id.hostname_edit_text);
		mHostnameField.setText(mHost.getHostname());
		mHostnameField.addTextChangedListener(new HostTextFieldWatcher(HostDatabase.FIELD_HOST_HOSTNAME));

		mPortContainer = view.findViewById(R.id.port_field_container);
		mPortField = (EditText) view.findViewById(R.id.port_edit_text);
		mPortField.setText(Integer.toString(mHost.getPort()));
		mPortField.addTextChangedListener(new HostTextFieldWatcher(HostDatabase.FIELD_HOST_PORT));

		mNicknameField = (EditText) view.findViewById(R.id.nickname_field);
		mNicknameField.setText(mHost.getNickname());
		mNicknameField.addTextChangedListener(
				new HostTextFieldWatcher(HostDatabase.FIELD_HOST_NICKNAME));

		mColorSelector = (Spinner) view.findViewById(R.id.color_selector);
		for (int i = 0; i < mColorValues.length(); i++) {
			if (mColorValues.getString(i).equals(mHost.getColor())) {
				mColorSelector.setSelection(i);
				break;
			}
		}
		mColorSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				mHost.setColor(mColorValues.getString(position));
				handleHostChange();
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});

		mFontSizeText = (TextView) view.findViewById(R.id.font_size_text);
		mFontSizeSeekBar = (SeekBar) view.findViewById(R.id.font_size_bar);
		mFontSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
			@Override
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
				int fontSize = MINIMUM_FONT_SIZE + progress;
				mHost.setFontSize(fontSize);
				handleHostChange();
				mFontSizeText.setText(Integer.toString(fontSize));
			}

			@Override
			public void onStartTrackingTouch(SeekBar seekBar) {
			}

			@Override
			public void onStopTrackingTouch(SeekBar seekBar) {
			}
		});
		mFontSizeSeekBar.setProgress(mHost.getFontSize() - MINIMUM_FONT_SIZE);

		mPubkeySpinner = (Spinner) view.findViewById(R.id.pubkey_spinner);
		final String[] pubkeyNames = new String[mPubkeyNames.size()];
		mPubkeyNames.toArray(pubkeyNames);
		ArrayAdapter<String> pubkeySelection = new ArrayAdapter<String>(
				getActivity(), android.R.layout.simple_spinner_item, pubkeyNames);
		pubkeySelection.setDropDownViewResource(
				android.R.layout.simple_spinner_dropdown_item);
		mPubkeySpinner.setAdapter(pubkeySelection);
		for (int i = 0; i < pubkeyNames.length; i++) {
			if (mHost.getPubkeyId() == Integer.parseInt(mPubkeyValues.get(i))) {
				mPubkeySpinner.setSelection(i);
				break;
			}
		}
		mPubkeySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				mHost.setPubkeyId(Integer.parseInt(mPubkeyValues.get(position)));
				handleHostChange();
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});

		mUseSshConfirmationContainer = view.findViewById(R.id.ssh_confirmation_container);
		mUseSshAuthSwitch = (SwitchCompat) view.findViewById(R.id.use_ssh_auth_switch);
		mSshAuthConfirmationCheckbox =
				(AppCompatCheckBox) view.findViewById(R.id.ssh_auth_confirmation_checkbox);
		CompoundButton.OnCheckedChangeListener authSwitchListener = new CompoundButton.OnCheckedChangeListener() {
			@Override
			public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
				mUseSshConfirmationContainer.setVisibility(
						mUseSshAuthSwitch.isChecked() ? View.VISIBLE : View.GONE);
				if (mUseSshAuthSwitch.isChecked()) {
					mHost.setUseAuthAgent(
							mSshAuthConfirmationCheckbox.isChecked() ?
									/* require confirmation */ mSshAuthValues.getString(1) :
									/* don't require confirmation */ mSshAuthValues.getString(2));
				} else {
					mHost.setUseAuthAgent(/* don't use */ mSshAuthValues.getString(0));
				}
				handleHostChange();
			}
		};
		if (mHost.getUseAuthAgent() == null ||
				mHost.getUseAuthAgent().equals(mSshAuthValues.getString(0))) {
			mUseSshAuthSwitch.setChecked(false);
			mSshAuthConfirmationCheckbox.setChecked(false);
		} else {
			mUseSshAuthSwitch.setChecked(true);
			mUseSshConfirmationContainer.setVisibility(View.VISIBLE);
			mSshAuthConfirmationCheckbox.setChecked(
					mHost.getUseAuthAgent().equals(mSshAuthValues.getString(1)));
		}
		mUseSshAuthSwitch.setOnCheckedChangeListener(authSwitchListener);
		mSshAuthConfirmationCheckbox.setOnCheckedChangeListener(authSwitchListener);

		mCompressionSwitch = (SwitchCompat) view.findViewById(R.id.compression_switch);
		mCompressionSwitch.setChecked(mHost.getCompression());
		mCompressionSwitch.setOnCheckedChangeListener(
				new HostSwitchWatcher(HostDatabase.FIELD_HOST_COMPRESSION));

		mStartShellSwitch = (SwitchCompat) view.findViewById(R.id.start_shell_switch);
		mStartShellSwitch.setChecked(mHost.getWantSession());
		mStartShellSwitch.setOnCheckedChangeListener(
				new HostSwitchWatcher(HostDatabase.FIELD_HOST_WANTSESSION));

		mStayConnectedSwitch = (SwitchCompat) view.findViewById(R.id.stay_connected_switch);
		mStayConnectedSwitch.setChecked(mHost.getStayConnected());
		mStayConnectedSwitch.setOnCheckedChangeListener(
				new HostSwitchWatcher(HostDatabase.FIELD_HOST_STAYCONNECTED));

		mCloseOnDisconnectSwitch = (SwitchCompat) view.findViewById(R.id.close_on_disconnect_switch);
		mCloseOnDisconnectSwitch.setChecked(mHost.getQuickDisconnect());
		mCloseOnDisconnectSwitch.setOnCheckedChangeListener(
				new HostSwitchWatcher(HostDatabase.FIELD_HOST_QUICKDISCONNECT));

		mPostLoginAutomationField = (EditText) view.findViewById(R.id.post_login_automation_field);
		mPostLoginAutomationField.setText(mHost.getPostLogin());
		mPostLoginAutomationField.addTextChangedListener(
				new HostTextFieldWatcher(HostDatabase.FIELD_HOST_POSTLOGIN));

		mDelKeySpinner = (Spinner) view.findViewById(R.id.del_key_spinner);
		for (int i = 0; i < mDelKeyValues.length(); i++) {
			if (mHost.getDelKey().equals(mDelKeyValues.getString(i))) {
				mDelKeySpinner.setSelection(i);
				break;
			}
		}
		mDelKeySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				mHost.setDelKey(mDelKeyValues.getString(position));
				handleHostChange();
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {
			}
		});

		mEncodingSpinner = (Spinner) view.findViewById(R.id.encoding_spinner);
		// The spinner is initialized in setCharsetData() because Charset data is not always
		// available when this fragment is created.

		setUriPartsContainerExpanded(mIsUriEditorExpanded);

		return view;
	}

	@Override
	public void onAttach(Context context) {
		super.onAttach(context);
		try {
			mListener = (Listener) context;
		} catch (ClassCastException e) {
			throw new ClassCastException(context.toString() + " must implement Listener");
		}

		// Now that the fragment is attached to an Activity, fetch the arrays from the attached
		// Activity's resources.
		mColorValues = getResources().obtainTypedArray(R.array.list_color_values);
		mSshAuthValues = getResources().obtainTypedArray(R.array.list_authagent_values);
		mDelKeyValues = getResources().obtainTypedArray(R.array.list_delkey_values);
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mListener = null;
		mColorValues.recycle();
		mSshAuthValues.recycle();
		mDelKeyValues.recycle();
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);

		savedInstanceState.putLong(ARG_EXISTING_HOST_ID, mHost.getId());
		savedInstanceState.putParcelable(ARG_EXISTING_HOST, mHost.getValues());
		savedInstanceState.putBoolean(ARG_IS_EXPANDED, mIsUriEditorExpanded);
		savedInstanceState.putString(
				ARG_QUICKCONNECT_STRING, mQuickConnectField.getText().toString());
		savedInstanceState.putStringArrayList(ARG_PUBKEY_NAMES, mPubkeyNames);
		savedInstanceState.putStringArrayList(ARG_PUBKEY_VALUES, mPubkeyValues);
	}

	/**
	 * Sets the Charset encoding data for the editor.
	 * @param data A map from Charset display name to Charset value (i.e., unique ID for the
	 *     Charset).
	 */
	public void setCharsetData(final Map<String, String> data) {
		if (mEncodingSpinner != null) {
			final String[] encodingNames = new String[data.keySet().size()];
			data.keySet().toArray(encodingNames);
			ArrayAdapter<String> encodingSelection = new ArrayAdapter<String>(
					getActivity(), android.R.layout.simple_spinner_item, encodingNames);
			encodingSelection.setDropDownViewResource(
					android.R.layout.simple_spinner_dropdown_item);
			mEncodingSpinner.setAdapter(encodingSelection);
			for (int i = 0; i < encodingNames.length; i++) {
				if (mHost.getEncoding() != null &&
						mHost.getEncoding().equals(data.get(encodingNames[i]))) {
					mEncodingSpinner.setSelection(i);
					break;
				}
			}
			mEncodingSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
				@Override
				public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
					mHost.setEncoding(data.get(encodingNames[position]));
					handleHostChange();
				}

				@Override
				public void onNothingSelected(AdapterView<?> parent) {
				}
			});
		}
	}

	private void setUriPartsContainerExpanded(boolean expanded) {
		mIsUriEditorExpanded = expanded;

		if (mIsUriEditorExpanded) {
			mExpandCollapseButton.setImageResource(R.drawable.ic_expand_less);
			mUriPartsContainer.setVisibility(View.VISIBLE);
		} else {
			mExpandCollapseButton.setImageResource(R.drawable.ic_expand_more);
			mUriPartsContainer.setVisibility(View.GONE);
		}
	}

	/**
	 * Applies the quick-connect URI entered in the field by copying its URI parts to mHost's
	 * fields.
	 * @param quickConnectString The URI entered in the quick-connect field.
	 * @param protocol The protocol for this connection.
	 */
	private void applyQuickConnectString(String quickConnectString, String protocol) {
		if (quickConnectString == null || protocol == null)
			return;

		Uri uri = TransportFactory.getUri(protocol, quickConnectString);
		if (uri == null) {
			// If the URI was invalid, null out the associated fields.
			mHost.setProtocol(protocol);
			mHost.setUsername(null);
			mHost.setHostname(null);
			mHost.setNickname(null);
			mHost.setPort(TransportFactory.getTransport(protocol).getDefaultPort());
			return;
		}

		HostBean host = TransportFactory.getTransport(protocol).createHost(uri);
		mHost.setProtocol(host.getProtocol());
		mHost.setUsername(host.getUsername());
		mHost.setHostname(host.getHostname());
		mHost.setNickname(host.getNickname());
		mHost.setPort(host.getPort());
		handleHostChange();
	}

	/**
	 * Handles a change in the host caused by the user adjusting the values of one of the widgets
	 * in this fragment. If the change has resulted in a valid host, the new value is sent back
	 * to the listener; however, if the change ha resulted in an invalid host, the listener is
	 * notified.
	 */
	private void handleHostChange() {
		String protocol = (String) mTransportSpinner.getSelectedItem();
		String quickConnectString = mQuickConnectField.getText().toString();
		if (protocol == null || protocol.equals("") ||
				quickConnectString == null || quickConnectString.equals("")) {
			// Invalid protocol and/or string, so don't do anything.
			mListener.onHostInvalidated();
			return;
		}

		Uri uri = TransportFactory.getUri(protocol, quickConnectString);
		if (uri == null) {
			// Valid string, but does not accurately describe a URI.
			mListener.onHostInvalidated();
			return;
		}

		// Now, the host is confirmed to have a valid URI.
		mListener.onValidHostConfigured(mHost);
	}

	public interface Listener {
		public void onValidHostConfigured(HostBean host);
		public void onHostInvalidated();
	}

	private class HostTextFieldWatcher implements TextWatcher {

		private final String mFieldType;

		public HostTextFieldWatcher(String fieldType) {
			mFieldType = fieldType;
		}

		@Override
		public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

		@Override
		public void onTextChanged(CharSequence s, int start, int before, int count) {}

		@Override
		public void afterTextChanged(Editable s) {
			String text = s.toString();

			if (HostDatabase.FIELD_HOST_USERNAME.equals(mFieldType)) {
				mHost.setUsername(text);
			} else if (HostDatabase.FIELD_HOST_HOSTNAME.equals(mFieldType)) {
				mHost.setHostname(text);
			} else if (HostDatabase.FIELD_HOST_PORT.equals(mFieldType)) {
				try {
					mHost.setPort(Integer.parseInt(text));
				} catch (NumberFormatException e) {
					return;
				}
			} else if (HostDatabase.FIELD_HOST_NICKNAME.equals(mFieldType)) {
				mHost.setNickname(text);
			} else if (HostDatabase.FIELD_HOST_POSTLOGIN.equals(mFieldType)) {
				mHost.setPostLogin(text);
			} else {
				throw new RuntimeException("Invalid field type.");
			}

			if (isUriRelatedField(mFieldType)) {
				mNicknameField.setText(mHost.toString());
				mHost.setNickname(mHost.toString());

				if (!mUriFieldEditInProgress) {
					mUriFieldEditInProgress = true;
					mQuickConnectField.setText(mHost.toString());
					mUriFieldEditInProgress = false;
				}
			}
			handleHostChange();
		}

		private boolean isUriRelatedField(String fieldType) {
			return HostDatabase.FIELD_HOST_USERNAME.equals(fieldType) ||
					HostDatabase.FIELD_HOST_HOSTNAME.equals(fieldType) ||
					HostDatabase.FIELD_HOST_PORT.equals(fieldType);
		}
	}

	private class HostSwitchWatcher implements CompoundButton.OnCheckedChangeListener {

		private final String mFieldType;

		public HostSwitchWatcher(String fieldType) {
			mFieldType = fieldType;
		}

		@Override
		public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
			if (HostDatabase.FIELD_HOST_COMPRESSION.equals(mFieldType)) {
				mHost.setCompression(isChecked);
			} else if (HostDatabase.FIELD_HOST_WANTSESSION.equals(mFieldType)) {
				mHost.setWantSession(isChecked);
			} else if (HostDatabase.FIELD_HOST_STAYCONNECTED.equals(mFieldType)) {
				mHost.setStayConnected(isChecked);
			} else if (HostDatabase.FIELD_HOST_QUICKDISCONNECT.equals(mFieldType)) {
				mHost.setQuickDisconnect(isChecked);
			} else {
				throw new RuntimeException("Invalid field type.");
			}
			handleHostChange();
		}
	}
}
