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

import android.content.ContentValues;
import android.content.Context;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.Fragment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
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

	private static final String ARG_EXISTING_HOST = "existingHost";
	private static final String ARG_IS_EXPANDED = "isExpanded";
	private static final String ARG_QUICKCONNECT_STRING = "quickConnectString";

	private static final int MINIMUM_FONT_SIZE = 8;

	// The host being edited.
	private HostBean mHost;
	
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

	public static HostEditorFragment newInstance(HostBean existingHost) {
		HostEditorFragment fragment = new HostEditorFragment();
		Bundle args = new Bundle();
		if (existingHost != null) {
			args.putParcelable(ARG_EXISTING_HOST, existingHost.getValues());
		}
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
		} else {
			mHost = new HostBean();
		}

		mIsUriEditorExpanded = bundle.getBoolean(ARG_IS_EXPANDED);
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		View view = inflater.inflate(R.layout.fragment_host_editor, container, false);

		mTransportSpinner = (Spinner) view.findViewById(R.id.transport_selector);
		ArrayAdapter<String> transportSelection = new ArrayAdapter<>(
				getActivity(),
				android.R.layout.simple_spinner_item,
				TransportFactory.getTransportNames());
		transportSelection.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		mTransportSpinner.setAdapter(transportSelection);
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
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before, int count) {}

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
		if (mHost.getColor() != null) {
			// Unfortunately, TypedArray doesn't have an indexOf(String) function, so search through
			// the array for the saved color.
			for (int i = 0; i < mColorValues.getIndexCount(); i++) {
				if (mHost.getColor().equals(mColorValues.getString(i))) {
					mColorSelector.setSelection(i);
					break;
				}
			}
		}
		mColorSelector.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				mHost.setColor(mColorValues.getString(position));
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

		// Now that the fragment is attached to an Activity, fetch the array from the attached
		// Activity's resources.
		mColorValues = getResources().obtainTypedArray(R.array.list_color_values);
	}

	@Override
	public void onDetach() {
		super.onDetach();
		mListener = null;
		mColorValues.recycle();
	}

	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);

		savedInstanceState.putParcelable(ARG_EXISTING_HOST, mHost.getValues());
		savedInstanceState.putBoolean(ARG_IS_EXPANDED, mIsUriEditorExpanded);
		savedInstanceState.putString(
				ARG_QUICKCONNECT_STRING, mQuickConnectField.getText().toString());
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
	}

	public interface Listener {
		public void onHostUpdated(HostBean host);
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
		}

		private boolean isUriRelatedField(String fieldType) {
			return HostDatabase.FIELD_HOST_USERNAME.equals(fieldType) ||
					HostDatabase.FIELD_HOST_HOSTNAME.equals(fieldType) ||
					HostDatabase.FIELD_HOST_PORT.equals(fieldType);
		}
	}
}
