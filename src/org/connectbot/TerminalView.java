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

import org.connectbot.bean.SelectionArea;
import org.connectbot.service.FontSizeChangedListener;
import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalKeyListener;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelXorXfermode;
import android.graphics.RectF;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.widget.Toast;
import de.mud.terminal.VDUBuffer;

/**
 * User interface {@link View} for showing a TerminalBridge in an
 * {@link Activity}. Handles drawing bitmap updates and passing keystrokes down
 * to terminal.
 *
 * @author jsharkey
 */
public class TerminalView extends View implements FontSizeChangedListener {

	private final Context context;
	public final TerminalBridge bridge;
	private final Paint paint;
	private final Paint cursorPaint;
	private final Paint cursorStrokePaint;

	// Cursor paints to distinguish modes
	private Path ctrlCursor, altCursor, shiftCursor;
	private RectF tempSrc, tempDst;
	private Matrix scaleMatrix;
	private static final Matrix.ScaleToFit scaleType = Matrix.ScaleToFit.FILL;

	private Toast notification = null;
	private String lastNotification = null;
	private volatile boolean notifications = true;

	public TerminalView(Context context, TerminalBridge bridge) {
		super(context);

		this.context = context;
		this.bridge = bridge;
		paint = new Paint();

		setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		setFocusable(true);
		setFocusableInTouchMode(true);

		cursorPaint = new Paint();
		cursorPaint.setColor(bridge.color[bridge.defaultFg]);
		cursorPaint.setXfermode(new PixelXorXfermode(bridge.color[bridge.defaultBg]));
		cursorPaint.setAntiAlias(true);

		cursorStrokePaint = new Paint(cursorPaint);
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

		bridge.addFontSizeChangedListener(this);

		// connect our view up to the bridge
		setOnKeyListener(bridge.getKeyHandler());
	}

	public void destroy() {
		// tell bridge to destroy its bitmap
		bridge.parentDestroyed();
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);

		bridge.parentChanged(this);

		scaleCursors();
	}

	public void onFontSizeChanged(float size) {
		scaleCursors();
	}

	private void scaleCursors() {
		// Create a scale matrix to scale our 1x1 representation of the cursor
		tempDst.set(0.0f, 0.0f, bridge.charWidth, bridge.charHeight);
		scaleMatrix.setRectToRect(tempSrc, tempDst, scaleType);
	}

	@Override
	public void onDraw(Canvas canvas) {
		if(bridge.bitmap != null) {
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

				int currentAttribute = bridge.buffer.getAttributes(
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
				canvas.drawPaint(cursorPaint);

				final int deadKey = bridge.getKeyHandler().getDeadKey();
				if (deadKey != 0) {
					canvas.drawText(new char[] { (char)deadKey }, 0, 1, 0, 0, cursorStrokePaint);
				}

				// Make sure we scale our decorations to the correct size.
				canvas.concat(scaleMatrix);

				int metaState = bridge.getKeyHandler().getMetaState();

				if ((metaState & TerminalKeyListener.META_SHIFT_ON) != 0)
					canvas.drawPath(shiftCursor, cursorStrokePaint);
				else if ((metaState & TerminalKeyListener.META_SHIFT_LOCK) != 0)
					canvas.drawPath(shiftCursor, cursorPaint);

				if ((metaState & TerminalKeyListener.META_ALT_ON) != 0)
					canvas.drawPath(altCursor, cursorStrokePaint);
				else if ((metaState & TerminalKeyListener.META_ALT_LOCK) != 0)
					canvas.drawPath(altCursor, cursorPaint);

				if ((metaState & TerminalKeyListener.META_CTRL_ON) != 0)
					canvas.drawPath(ctrlCursor, cursorStrokePaint);
				else if ((metaState & TerminalKeyListener.META_CTRL_LOCK) != 0)
					canvas.drawPath(ctrlCursor, cursorPaint);

				// Restore previous clip region
				canvas.restore();
			}

			// draw any highlighted area
			if (bridge.isSelectingForCopy()) {
				SelectionArea area = bridge.getSelectionArea();
				canvas.save(Canvas.CLIP_SAVE_FLAG);
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
	 * @param width
	 * @param height
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
		outAttrs.inputType = EditorInfo.TYPE_NULL;
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
}
