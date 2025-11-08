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

import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.preference.PreferenceManager;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import org.connectbot.R;
import org.connectbot.service.TerminalBridge;

/**
 * A floating dialog that provides text input using the standard Android keyboard.
 * This allows users to access features like swipe typing and voice dictation.
 */
public class FloatingInputDialog extends Dialog {
	private static final String PREF_FLOATING_X = "floating_input_x";
	private static final String PREF_FLOATING_Y = "floating_input_y";

	private final Context context;
	private final SharedPreferences prefs;
	private EditText inputText;
	private TerminalBridge currentBridge;

	// Variables for drag functionality
	private float initialTouchX;
	private float initialTouchY;
	private int initialX;
	private int initialY;
	private boolean isDragging = false;

	public FloatingInputDialog(Context context) {
		super(context, android.R.style.Theme_Translucent_NoTitleBar);
		this.context = context;
		this.prefs = PreferenceManager.getDefaultSharedPreferences(context);
		initializeDialog();
	}

	private void initializeDialog() {
		// Remove title and make background transparent
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
		
		// Set window flags for floating behavior
		getWindow().setFlags(
			WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
			WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
		getWindow().setFlags(
			WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
			WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH);
		
		// Inflate the layout
		LayoutInflater inflater = LayoutInflater.from(context);
		View contentView = inflater.inflate(R.layout.floating_input_window, null);
		setContentView(contentView);

		// Get screen width and calculate window width (90% of screen)
		android.util.DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
		int screenWidth = displayMetrics.widthPixels;
		int windowWidth = (int) (screenWidth * 0.9f);

		// Set dialog window parameters
		WindowManager.LayoutParams params = getWindow().getAttributes();
		params.width = windowWidth;
		params.height = WindowManager.LayoutParams.WRAP_CONTENT;
		params.gravity = Gravity.TOP | Gravity.LEFT;
		params.flags &= ~WindowManager.LayoutParams.FLAG_DIM_BEHIND;
		getWindow().setAttributes(params);

		// Get references to views
		inputText = findViewById(R.id.floating_input_text);
		ImageButton closeButton = findViewById(R.id.floating_input_close);
		ImageButton submitButton = findViewById(R.id.floating_input_submit);
		RelativeLayout header = findViewById(R.id.floating_input_header);

		// Set up close button
		closeButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				dismiss();
			}
		});

		// Set up submit button
		submitButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				submitText();
			}
		});

		// Set up drag functionality on header
		header.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				return handleDragTouch(event);
			}
		});

		// Handle outside touch to prevent accidental dismissal
		setCanceledOnTouchOutside(false);
	}

	private boolean handleDragTouch(MotionEvent event) {
		WindowManager.LayoutParams params = getWindow().getAttributes();
		
		switch (event.getAction()) {
			case MotionEvent.ACTION_DOWN:
				// Record initial touch position
				initialTouchX = event.getRawX();
				initialTouchY = event.getRawY();

				// Get current window position
				initialX = params.x;
				initialY = params.y;

				isDragging = false;
				return true;

			case MotionEvent.ACTION_MOVE:
				// Calculate the moved distance
				float deltaX = event.getRawX() - initialTouchX;
				float deltaY = event.getRawY() - initialTouchY;

				// Consider it dragging if moved more than a small threshold
				if (Math.abs(deltaX) > 5 || Math.abs(deltaY) > 5) {
					isDragging = true;
				}

				if (isDragging) {
					// Update window position
					params.x = (int) (initialX + deltaX);
					params.y = (int) (initialY + deltaY);
					getWindow().setAttributes(params);
				}
				return true;

			case MotionEvent.ACTION_UP:
				if (isDragging) {
					// Save the new position
					savePosition();
				}
				return true;
		}
		return false;
	}

	private void savePosition() {
		WindowManager.LayoutParams params = getWindow().getAttributes();
		SharedPreferences.Editor editor = prefs.edit();
		editor.putInt(PREF_FLOATING_X, params.x);
		editor.putInt(PREF_FLOATING_Y, params.y);
		editor.apply();
	}

	private void submitText() {
		String text = inputText.getText().toString();
		if (!text.isEmpty() && currentBridge != null) {
			currentBridge.injectString(text);
			inputText.setText("");
			dismiss();
		}
	}

	public void show(TerminalBridge bridge) {
		show(bridge, "");
	}

	public void show(TerminalBridge bridge, String initialText) {
		this.currentBridge = bridge;

		// Set initial text if provided
		if (initialText != null && !initialText.isEmpty()) {
			inputText.setText(initialText);
			inputText.setSelection(initialText.length());
		}

		// Get screen dimensions
		android.util.DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
		int screenWidth = displayMetrics.widthPixels;
		int windowWidth = (int) (screenWidth * 0.9f);

		// Get saved position or use defaults (center horizontally)
		int defaultX = (screenWidth - windowWidth) / 2;
		int x = prefs.getInt(PREF_FLOATING_X, defaultX);
		int y = prefs.getInt(PREF_FLOATING_Y, 200);

		// Set window position
		WindowManager.LayoutParams params = getWindow().getAttributes();
		params.x = x;
		params.y = y;
		getWindow().setAttributes(params);

		// Show the dialog
		show();

		// Request focus and show keyboard
		inputText.requestFocus();
		InputMethodManager imm = (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
		imm.showSoftInput(inputText, InputMethodManager.SHOW_IMPLICIT);
	}

	@Override
	public void onBackPressed() {
		// Don't dismiss on back press - user must use close button
	}
}