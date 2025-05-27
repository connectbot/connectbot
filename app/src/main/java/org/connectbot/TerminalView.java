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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.connectbot.bean.SelectionArea;
import org.connectbot.service.FontSizeChangedListener;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalKeyListener;
import org.connectbot.util.PreferenceConstants;
import org.connectbot.util.TerminalTextViewOverlay;
import org.connectbot.util.TerminalViewPager;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.ClipboardManager;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.FrameLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;
import de.mud.terminal.VDUBuffer;
import de.mud.terminal.vt320;

/**
 * User interface {@link View} for showing a TerminalBridge in an
 * {@link android.app.Activity}. Handles drawing bitmap updates and passing keystrokes down
 * to terminal.
 *
 * @author jsharkey
 */
public class TerminalView extends FrameLayout implements FontSizeChangedListener {
	private final Context context;
	public final TerminalBridge bridge;

	private final TerminalTextViewOverlay terminalTextViewOverlay;
	public final TerminalViewPager viewPager;
	private final GestureDetector gestureDetector;
	private ArrowGestureListener gestureListener;
	private final SharedPreferences prefs;

	// These are only used for pre-Honeycomb copying.
	private int lastTouchedRow, lastTouchedCol;
	private final ClipboardManager clipboard;

	private final Paint paint;
	private final Paint cursorPaint;
	private final Paint cursorStrokePaint;
	private final Paint cursorInversionPaint;
	private final Paint cursorMetaInversionPaint;

	// Cursor paints to distinguish modes
	private final Path ctrlCursor;
	private final Path altCursor;
	private final Path shiftCursor;
	private final RectF tempSrc;
	private final RectF tempDst;
	private final Matrix scaleMatrix;
	private static final Matrix.ScaleToFit scaleType = Matrix.ScaleToFit.FILL;

	private Toast notification = null;
	private String lastNotification = null;
	private volatile boolean notifications = true;

	// Related to Accessibility Features
	private boolean mAccessibilityInitialized = false;
	private boolean mAccessibilityActive = true;
	private final Object[] mAccessibilityLock = new Object[0];
	private final StringBuffer mAccessibilityBuffer;
	private Pattern mControlCodes = null;
	private Matcher mCodeMatcher = null;
	private AccessibilityEventSender mEventSender = null;

	private final char[] singleDeadKey = new char[1];

	private static final String BACKSPACE_CODE = "\\x08\\x1b\\[K";
	private static final String CONTROL_CODE_PATTERN = "\\x1b\\[K[^m]+[m|:]";

	private static final int ACCESSIBILITY_EVENT_THRESHOLD = 1000;
	private static final String SCREENREADER_INTENT_ACTION = "android.accessibilityservice.AccessibilityService";
	private static final String SCREENREADER_INTENT_CATEGORY = "android.accessibilityservice.category.FEEDBACK_SPOKEN";

	public TerminalView(Context context, TerminalBridge bridge, TerminalViewPager pager) {
		super(context);

		setWillNotDraw(false);

		this.context = context;
		this.bridge = bridge;
		this.viewPager = pager;
		mAccessibilityBuffer = new StringBuffer();

		setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		setFocusable(true);
		setFocusableInTouchMode(true);

		// Some things TerminalView uses is unsupported in hardware acceleration
		// so this is using software rendering until we can replace all the
		// instances.
		// See: https://developer.android.com/guide/topics/graphics/hardware-accel.html#unsupported
		setLayerTypeToSoftware();

		paint = new Paint();

		cursorPaint = new Paint();
		cursorPaint.setColor(bridge.color[bridge.defaultFg]);
		cursorPaint.setAntiAlias(true);

		cursorInversionPaint = new Paint();
		cursorInversionPaint.setColorFilter(new ColorMatrixColorFilter(new ColorMatrix(new float[] {
				-1, 0, 0, 0, 255,
				0, -1, 0, 0, 255,
				0, 0, -1, 0, 255,
				0, 0, 0, 1, 0
		})));
		cursorInversionPaint.setAntiAlias(true);

		cursorMetaInversionPaint = new Paint();
		cursorMetaInversionPaint.setColorFilter(
				new ColorMatrixColorFilter(new ColorMatrix(new float[] {
						-1f, 0, 0, 0, 255,
						0, -1f, 0, 0, 255,
						0, 0, -1f, 0, 255,
						0, 0, 0, 0.5f, 0
				})));
		cursorMetaInversionPaint.setAntiAlias(true);

		cursorStrokePaint = new Paint(cursorInversionPaint);
		cursorStrokePaint.setStrokeWidth(0.1f);
		cursorStrokePaint.setStyle(Paint.Style.STROKE);

		/*
		 * Set up our cursor indicators on a 1x1 Path object which we can later
		 * transform to our character width and height
		 */
		// TODO make this into a resource somehow
		shiftCursor = new Path();
		shiftCursor.lineTo(0.5f, 0.33f);
		shiftCursor.lineTo(1.0f, 0.0f);

		altCursor = new Path();
		altCursor.moveTo(0.0f, 1.0f);
		altCursor.lineTo(0.5f, 0.66f);
		altCursor.lineTo(1.0f, 1.0f);

		ctrlCursor = new Path();
		ctrlCursor.moveTo(0.0f, 0.25f);
		ctrlCursor.lineTo(1.0f, 0.5f);
		ctrlCursor.lineTo(0.0f, 0.75f);

		// For creating the transform when the terminal resizes
		tempSrc = new RectF();
		tempSrc.set(0.0f, 0.0f, 1.0f, 1.0f);
		tempDst = new RectF();
		scaleMatrix = new Matrix();

		// connect our view up to the bridge
		setOnKeyListener(bridge.getKeyHandler());

		terminalTextViewOverlay = new TerminalTextViewOverlay(context, this);
		terminalTextViewOverlay.setLayoutParams(
				new RelativeLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
		addView(terminalTextViewOverlay, 0);

		// Once terminalTextViewOverlay is active, allow it to handle key events instead.
		terminalTextViewOverlay.setOnKeyListener(bridge.getKeyHandler());

		clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
		prefs = PreferenceManager.getDefaultSharedPreferences(context);

		bridge.addFontSizeChangedListener(this);
		bridge.parentChanged(this);

		onFontSizeChanged(0); // the argument is unused

		gestureListener = new ArrowGestureListener();
		gestureDetector = new GestureDetector(context, gestureListener);

		// Enable accessibility features if a screen reader is active.
		new AccessibilityStateTester().execute((Void) null);
	}

	private void setLayerTypeToSoftware() {
		setLayerType(View.LAYER_TYPE_SOFTWARE, null);
	}

	public void copyCurrentSelectionToClipboard() {
		if (terminalTextViewOverlay != null) {
			terminalTextViewOverlay.copyCurrentSelectionToClipboard();
		}
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		// Check if we should disable ViewPager for arrow gestures
		boolean arrowKeyGestureEnabled = prefs.getBoolean(PreferenceConstants.ARROW_KEY_GESTURE, false);
		float xPos = event.getX();
		float screenWidth = getWidth();
		boolean inArrowGestureZone = arrowKeyGestureEnabled && xPos <= screenWidth * 2 / 3;
		
		// Disable ViewPager paging when in arrow gesture zone
		if (event.getAction() == MotionEvent.ACTION_DOWN && inArrowGestureZone) {
			viewPager.setPagingEnabled(false);
		} else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
			viewPager.setPagingEnabled(true);
			// Stop key repeat when touch ends
			if (gestureListener != null) {
				gestureListener.stopKeyRepeat();
			}
		}
		
		if (gestureDetector != null && gestureDetector.onTouchEvent(event)) {
			return true;
		}

		// Old version of copying, only for pre-Honeycomb.
		if (terminalTextViewOverlay == null) {
			// when copying, highlight the area
			if (bridge.isSelectingForCopy()) {
				SelectionArea area = bridge.getSelectionArea();
				int row = (int) Math.floor(event.getY() / bridge.charHeight);
				int col = (int) Math.floor(event.getX() / bridge.charWidth);

				switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
					// recording starting area
					viewPager.setPagingEnabled(false);
					if (area.isSelectingOrigin()) {
						area.setRow(row);
						area.setColumn(col);
						lastTouchedRow = row;
						lastTouchedCol = col;
						bridge.redraw();
					}
					return true;
				case MotionEvent.ACTION_MOVE:
							/* ignore when user hasn't moved since last time so
							 * we can fine-tune with directional pad
							 */
					if (row == lastTouchedRow && col == lastTouchedCol)
						return true;

					// if the user moves, start the selection for other corner
					area.finishSelectingOrigin();

					// update selected area
					area.setRow(row);
					area.setColumn(col);
					lastTouchedRow = row;
					lastTouchedCol = col;
					bridge.redraw();
					return true;
				case MotionEvent.ACTION_UP:
							/* If they didn't move their finger, maybe they meant to
							 * select the rest of the text with the directional pad.
							 */
					if (area.getLeft() == area.getRight() &&
							area.getTop() == area.getBottom()) {
						return true;
					}

					// copy selected area to clipboard
					String copiedText = area.copyFrom(bridge.buffer);

					clipboard.setText(copiedText);
					Toast.makeText(
							context,
							context.getResources().getQuantityString(R.plurals.console_copy_done,
									copiedText.length(), copiedText.length()),
							Toast.LENGTH_LONG).show();

					// fall through to clear state

				case MotionEvent.ACTION_CANCEL:
					// make sure we clear any highlighted area
					area.reset();
					bridge.setSelectingForCopy(false);
					bridge.redraw();
					viewPager.setPagingEnabled(true);
					return true;
				}
			}

			return true;
		}

		return super.onTouchEvent(event);
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);

		bridge.parentChanged(this);

		scaleCursors();
	}

	@Override
	public void onFontSizeChanged(final float unusedSizeDp) {
		scaleCursors();

		((Activity) context).runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (terminalTextViewOverlay != null) {
					// Use the bridge text size in pixels to have exactly the same text size.
					terminalTextViewOverlay.setTextSize(TypedValue.COMPLEX_UNIT_PX, bridge.getTextSizePx());

					// For the TextView to line up with the bitmap text, lineHeight must be equal to
					// the bridge's charHeight. See TextView.getLineHeight(), which has been reversed to
					// derive lineSpacingMultiplier.
					float lineSpacingMultiplier = (float) bridge.charHeight / terminalTextViewOverlay.getPaint().getFontMetricsInt(null);
					terminalTextViewOverlay.setLineSpacing(0.0f, lineSpacingMultiplier);
				}
			}
		});
	}

	private void scaleCursors() {
		// Create a scale matrix to scale our 1x1 representation of the cursor
		tempDst.set(0.0f, 0.0f, bridge.charWidth, bridge.charHeight);
		scaleMatrix.setRectToRect(tempSrc, tempDst, scaleType);
	}

	@Override
	public void onDraw(Canvas canvas) {
		if (bridge.bitmap != null) {
			// draw the bitmap
			bridge.onDraw();

			// draw the bridge bitmap if it exists
			canvas.drawBitmap(bridge.bitmap, 0, 0, paint);

			// also draw cursor if visible
			if (bridge.buffer.isCursorVisible()) {
				int cursorColumn = bridge.buffer.getCursorColumn();
				final int cursorRow = bridge.buffer.getCursorRow();

				final int columns = bridge.buffer.getColumns();

				if (cursorColumn == columns)
					cursorColumn = columns - 1;

				if (cursorColumn < 0 || cursorRow < 0)
					return;

				long currentAttribute = bridge.buffer.getAttributes(
						cursorColumn, cursorRow);
				boolean onWideCharacter = (currentAttribute & VDUBuffer.FULLWIDTH) != 0;

				int x = cursorColumn * bridge.charWidth;
				int y = (bridge.buffer.getCursorRow()
						+ bridge.buffer.screenBase - bridge.buffer.windowBase)
						* bridge.charHeight;

				// Save the current clip and translation
				canvas.save();

				canvas.translate(x, y);
				canvas.clipRect(0, 0,
						bridge.charWidth * (onWideCharacter ? 2 : 1),
						bridge.charHeight);

				int metaState = bridge.getKeyHandler().getMetaState();
				// Check bitmap bounds before creating sub-bitmap
				int cursorWidth = bridge.charWidth * (onWideCharacter ? 2 : 1);
				if (y + bridge.charHeight <= bridge.bitmap.getHeight() && 
				    x + cursorWidth <= bridge.bitmap.getWidth() &&
				    x >= 0 && y >= 0) {
					Bitmap underCursor = Bitmap.createBitmap(bridge.bitmap, x, y,
							cursorWidth, bridge.charHeight);
					if (metaState == 0)
						canvas.drawBitmap(underCursor, 0, 0, cursorInversionPaint);
					else
						canvas.drawBitmap(underCursor, 0, 0, cursorMetaInversionPaint);
				} else {
					canvas.drawPaint(cursorPaint);
				}
				final int deadKey = bridge.getKeyHandler().getDeadKey();
				if (deadKey != 0) {
					singleDeadKey[0] = (char) deadKey;
					canvas.drawText(singleDeadKey, 0, 1, 0, 0, cursorStrokePaint);
				}

				// Make sure we scale our decorations to the correct size.
				canvas.concat(scaleMatrix);

				if ((metaState & TerminalKeyListener.OUR_SHIFT_ON) != 0)
					canvas.drawPath(shiftCursor, cursorStrokePaint);
				else if ((metaState & TerminalKeyListener.OUR_SHIFT_LOCK) != 0)
					canvas.drawPath(shiftCursor, cursorInversionPaint);

				if ((metaState & TerminalKeyListener.OUR_ALT_ON) != 0)
					canvas.drawPath(altCursor, cursorStrokePaint);
				else if ((metaState & TerminalKeyListener.OUR_ALT_LOCK) != 0)
					canvas.drawPath(altCursor, cursorInversionPaint);

				if ((metaState & TerminalKeyListener.OUR_CTRL_ON) != 0)
					canvas.drawPath(ctrlCursor, cursorStrokePaint);
				else if ((metaState & TerminalKeyListener.OUR_CTRL_LOCK) != 0)
					canvas.drawPath(ctrlCursor, cursorInversionPaint);

				// Restore previous clip region
				canvas.restore();
			}

			// draw any highlighted area
			if (terminalTextViewOverlay == null && bridge.isSelectingForCopy()) {
				SelectionArea area = bridge.getSelectionArea();
				canvas.save();
				canvas.clipRect(
					area.getLeft() * bridge.charWidth,
					area.getTop() * bridge.charHeight,
					(area.getRight() + 1) * bridge.charWidth,
					(area.getBottom() + 1) * bridge.charHeight
				);
				canvas.drawPaint(cursorPaint);
				canvas.restore();
			}
		}
	}

	public void notifyUser(String message) {
		if (!notifications)
			return;

		if (notification != null) {
			// Don't keep telling the user the same thing.
			if (lastNotification != null && lastNotification.equals(message))
				return;

			notification.setText(message);
			notification.show();
		} else {
			notification = Toast.makeText(context, message, Toast.LENGTH_SHORT);
			notification.show();
		}

		lastNotification = message;
	}

	/**
	 * Ask the {@link TerminalBridge} we're connected to to resize to a specific size.
	 * @param width width in characters
	 * @param height heigh in characters
	 */
	public void forceSize(int width, int height) {
		bridge.resizeComputed(width, height, getWidth(), getHeight());
	}

	/**
	 * Sets the ability for the TerminalView to display Toast notifications to the user.
	 * @param value whether to enable notifications or not
	 */
	public void setNotifications(boolean value) {
		notifications = value;
	}

	@Override
	public boolean onCheckIsTextEditor() {
		return true;
	}

	@Override
	public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
		outAttrs.imeOptions |=
			EditorInfo.IME_FLAG_NO_EXTRACT_UI |
			EditorInfo.IME_FLAG_NO_ENTER_ACTION |
			EditorInfo.IME_ACTION_NONE;
		// Turn off suggestions
		outAttrs.inputType = EditorInfo.TYPE_NULL |
				EditorInfo.TYPE_TEXT_VARIATION_PASSWORD |
				EditorInfo.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD |
				EditorInfo.TYPE_TEXT_FLAG_NO_SUGGESTIONS;
		return new BaseInputConnection(this, false) {
			@Override
			public boolean deleteSurroundingText (int leftLength, int rightLength) {
				if (rightLength == 0 && leftLength == 0) {
					return this.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
				}
				for (int i = 0; i < leftLength; i++) {
					this.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
				}
				// TODO: forward delete
				return true;
			}
		};
	}

	public void propagateConsoleText(char[] rawText, int length) {
		if (mAccessibilityActive) {
			synchronized (mAccessibilityLock) {
				mAccessibilityBuffer.append(rawText, 0, length);
			}

			if (mAccessibilityInitialized) {
				if (mEventSender != null) {
					removeCallbacks(mEventSender);
				} else {
					mEventSender = new AccessibilityEventSender();
				}

				postDelayed(mEventSender, ACCESSIBILITY_EVENT_THRESHOLD);
			}
		}

		((Activity) context).runOnUiThread(new Runnable() {
			@Override
			public void run() {
				if (terminalTextViewOverlay != null) {
					terminalTextViewOverlay.onBufferChanged();
				}
			}
		});
	}

	private class AccessibilityEventSender implements Runnable {
		@Override
		public void run() {
			synchronized (mAccessibilityLock) {
				// Initialize mControlCodes if it's null
				if (mControlCodes == null) {
					mControlCodes = Pattern.compile(CONTROL_CODE_PATTERN);
				}
				
				if (mCodeMatcher == null) {
					mCodeMatcher = mControlCodes.matcher(mAccessibilityBuffer.toString());
				} else {
					mCodeMatcher.reset(mAccessibilityBuffer.toString());
				}

				// Strip all control codes out.
				mAccessibilityBuffer.setLength(0);
				while (mCodeMatcher.find()) {
					mCodeMatcher.appendReplacement(mAccessibilityBuffer, " ");
				}

				// Apply Backspaces using backspace character sequence
				int i = mAccessibilityBuffer.indexOf(BACKSPACE_CODE);
				while (i != -1) {
					mAccessibilityBuffer.replace(i == 0 ? 0 : i - 1,
							i + BACKSPACE_CODE.length(), "");
					i = mAccessibilityBuffer.indexOf(BACKSPACE_CODE);
				}

				if (mAccessibilityBuffer.length() > 0) {
					AccessibilityEvent event = AccessibilityEvent.obtain(
							AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED);
					event.setFromIndex(0);
					event.setAddedCount(mAccessibilityBuffer.length());
					event.getText().add(mAccessibilityBuffer);

					sendAccessibilityEventUnchecked(event);
					mAccessibilityBuffer.setLength(0);
				}
			}
		}
	}

	private class AccessibilityStateTester extends AsyncTask<Void, Void, Boolean> {
		@Override
		protected Boolean doInBackground(Void... params) {
			/*
			 * Presumably if the accessibility manager is not enabled, we don't
			 * need to send accessibility events.
			 */
			final AccessibilityManager accessibility = (AccessibilityManager) context
					.getSystemService(Context.ACCESSIBILITY_SERVICE);
			if (!accessibility.isEnabled()) {
				return Boolean.FALSE;
			}

			/*
			 * Restrict the set of intents to only accessibility services that
			 * have the category FEEDBACK_SPOKEN (aka, screen readers).
			 */
			final Intent screenReaderIntent = new Intent(SCREENREADER_INTENT_ACTION);
			screenReaderIntent.addCategory(SCREENREADER_INTENT_CATEGORY);

			final ContentResolver cr = context.getContentResolver();

			final List<ResolveInfo> screenReaders = context.getPackageManager().queryIntentServices(
					screenReaderIntent, 0);

			boolean foundScreenReader = false;

			for (ResolveInfo screenReader : screenReaders) {
				/*
				 * All screen readers are expected to implement a content
				 * provider that responds to:
				 * content://<nameofpackage>.providers.StatusProvider
				 */
				final Cursor cursor = cr.query(
						Uri.parse("content://" + screenReader.serviceInfo.packageName
								+ ".providers.StatusProvider"), null, null, null, null);
				if (cursor != null) {
					try {
						if (!cursor.moveToFirst()) {
							continue;
						}

						/*
						 * These content providers use a special cursor that only has
						 * one element, an integer that is 1 if the screen reader is
						 * running.
						 */
						final int status = cursor.getInt(0);

						if (status == 1) {
							foundScreenReader = true;
							break;
						}
					} finally {
						cursor.close();
					}
				}
			}

			if (foundScreenReader) {
				mControlCodes = Pattern.compile(CONTROL_CODE_PATTERN);
			}

			return foundScreenReader;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			mAccessibilityActive = result;

			mAccessibilityInitialized = true;

			if (result) {
				mEventSender = new AccessibilityEventSender();
				postDelayed(mEventSender, ACCESSIBILITY_EVENT_THRESHOLD);
			} else {
				synchronized (mAccessibilityLock) {
					mAccessibilityBuffer.setLength(0);
					mAccessibilityBuffer.trimToSize();
				}
			}
		}
	}
	
	private class ArrowGestureListener extends GestureDetector.SimpleOnGestureListener {
		// Only used for pre-Honeycomb devices.
		private final TerminalBridge bridge = TerminalView.this.bridge;
		private float totalY = 0;
		private float totalX = 0;
		private boolean gestureHandled = false;
		private int currentKey = 0;
		private Handler keyRepeatHandler = new Handler();
		private static final int KEY_REPEAT_INITIAL_DELAY = 400; // ms before first repeat
		private static final int KEY_REPEAT_INTERVAL_VERTICAL = 150; // ms between repeats for up/down (slower)
		private static final int KEY_REPEAT_INTERVAL_HORIZONTAL = 75; // ms between repeats for left/right (faster)
		
		private Runnable keyRepeatRunnable = new Runnable() {
			@Override
			public void run() {
				if (currentKey != 0) {
					((vt320) bridge.buffer).keyPressed(currentKey, ' ', 0);
					bridge.tryKeyVibrate();
					
					// Use different repeat intervals for vertical vs horizontal movement
					int interval = (currentKey == vt320.KEY_UP || currentKey == vt320.KEY_DOWN) 
							? KEY_REPEAT_INTERVAL_VERTICAL 
							: KEY_REPEAT_INTERVAL_HORIZONTAL;
					
					keyRepeatHandler.postDelayed(this, interval);
				}
			}
		};

		/**
		 * This should only handle scrolling when terminalTextViewOverlay is {@code null}, but
		 * we need to handle the page up/down gesture if it's enabled.
		 */
		@Override
		public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
			// Check if arrow key gesture is enabled
			boolean arrowKeyGestureEnabled = 
					prefs.getBoolean(PreferenceConstants.ARROW_KEY_GESTURE, false);
			boolean pgUpDnGestureEnabled =
					prefs.getBoolean(PreferenceConstants.PG_UPDN_GESTURE, false);
			
			int touchSlop = ViewConfiguration.get(TerminalView.this.context).getScaledTouchSlop();
			float screenWidth = getWidth();
			float xPos = e2.getX();
			
			// Check if we're in arrow gesture zone
			boolean inArrowGestureZone = arrowKeyGestureEnabled && xPos <= screenWidth * 2 / 3;
			
			// Handle arrow key gestures in left 2/3 of screen
			if (inArrowGestureZone) {
				// Accumulate total movement
				totalX += distanceX;
				totalY += distanceY;
				
				// Check if we've moved enough to trigger a gesture
				float threshold = touchSlop * 3;
				
				// Determine dominant direction
				if (Math.abs(totalX) > threshold || Math.abs(totalY) > threshold) {
					if (!gestureHandled) {
						int keyToSend = 0;
						if (Math.abs(totalX) > Math.abs(totalY)) {
							// Horizontal swipe
							if (totalX > threshold) {
								// Swipe left (finger moves left, content moves right)
								keyToSend = vt320.KEY_LEFT;
							} else if (totalX < -threshold) {
								// Swipe right (finger moves right, content moves left)
								keyToSend = vt320.KEY_RIGHT;
							}
						} else {
							// Vertical swipe
							if (totalY > threshold) {
								// Swipe up (finger moves up, content moves down)
								keyToSend = vt320.KEY_UP;
							} else if (totalY < -threshold) {
								// Swipe down (finger moves down, content moves up)
								keyToSend = vt320.KEY_DOWN;
							}
						}
						
						if (keyToSend != 0) {
							// Send the arrow key
							((vt320) bridge.buffer).keyPressed(keyToSend, ' ', 0);
							bridge.tryKeyVibrate();
							gestureHandled = true;
							currentKey = keyToSend;
							
							// Start key repeat after delay
							keyRepeatHandler.postDelayed(keyRepeatRunnable, KEY_REPEAT_INITIAL_DELAY);
						}
					}
				}
				// ALWAYS return true in gesture zone to prevent any scrolling
				return true;
			}
			// Handle page up/down in left 1/3 (only if arrow gestures not enabled or not in that area)
			else if (pgUpDnGestureEnabled && xPos <= screenWidth / 3 && !arrowKeyGestureEnabled) {
				if (Math.abs(e1.getX() - e2.getX()) < touchSlop * 4) {
					totalY += distanceY;
					final int moved = (int) (totalY / bridge.charHeight);
					
					if (moved > 5) {
						((vt320) bridge.buffer).keyPressed(vt320.KEY_PAGE_DOWN, ' ', 0);
						bridge.tryKeyVibrate();
						totalY = 0;
					} else if (moved < -5) {
						((vt320) bridge.buffer).keyPressed(vt320.KEY_PAGE_UP, ' ', 0);
						bridge.tryKeyVibrate();
						totalY = 0;
					}
					return true;
				}
			}
			// Handle normal scrolling in right portion or when gestures disabled
			else if (!gestureHandled) {  // Don't scroll if we handled a gesture
				if (Math.abs(e1.getX() - e2.getX()) < touchSlop * 4) {
					totalY += distanceY;
					final int moved = (int) (totalY / bridge.charHeight);
					
					if (moved != 0) {
						int base = bridge.buffer.getWindowBase();
						bridge.buffer.setWindowBase(base + moved);
						totalY = 0;
						return false;
					}
				}
			}

			return gestureHandled;  // Return true if we handled a gesture to prevent further processing
		}

		@Override
		public boolean onDown(MotionEvent e) {
			// Reset gesture tracking
			totalX = 0;
			totalY = 0;
			gestureHandled = false;
			currentKey = 0;
			keyRepeatHandler.removeCallbacks(keyRepeatRunnable);
			return false;
		}

		@Override
		public boolean onSingleTapConfirmed(MotionEvent e) {
			viewPager.performClick();
			return super.onSingleTapConfirmed(e);
		}
		
		public void stopKeyRepeat() {
			currentKey = 0;
			keyRepeatHandler.removeCallbacks(keyRepeatRunnable);
		}
	}
}
