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

import org.connectbot.bean.SelectionArea;
import org.connectbot.service.TerminalBridge;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelXorXfermode;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Toast;

/**
 * User interface {@link View} for showing a TerminalBridge in an
 * {@link Activity}. Handles drawing bitmap updates and passing keystrokes down
 * to terminal.
 *
 * @author jsharkey
 */
public class TerminalView extends View {

	private final Context context;
	public final TerminalBridge bridge;
	private final Paint paint;
	private final Paint cursorPaint;
	private final Paint cursorDecorationPaint;

	// Cursor paints to distinguish modes
	private float[] ctrlLines, altLines, shiftLines;

	private Toast notification = null;
	private String lastNotification = null;

	public TerminalView(Context context, TerminalBridge bridge) {
		super(context);

		this.context = context;
		this.bridge = bridge;
		paint = new Paint();

		setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		setFocusable(true);
		setFocusableInTouchMode(true);

		cursorPaint = new Paint();
		cursorPaint.setColor(bridge.color[TerminalBridge.COLOR_FG_STD]);
		cursorPaint.setXfermode(new PixelXorXfermode(bridge.color[TerminalBridge.COLOR_BG_STD]));

		cursorDecorationPaint = new Paint();
		cursorDecorationPaint.setColor(Color.BLACK);
		//cursorDecorationPaint.setXfermode(new PixelXorXfermode(bridge.color[TerminalBridge.COLOR_FG_STD]));

		// connect our view up to the bridge
		setOnKeyListener(bridge);
	}

	public void destroy() {
		// tell bridge to destroy its bitmap
		bridge.parentDestroyed();
	}

	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		bridge.parentChanged(this);

		// Make a triangle shape pointing down on the top
		shiftLines = new float[] {
			0.0f, 0.0f, bridge.charWidth / 2.0f, bridge.charHeight / 3.0f,
			bridge.charWidth / 2.0f, bridge.charHeight / 3.0f, bridge.charWidth, 0.0f,
			bridge.charWidth, 0.0f, 0.0f, 0.0f,
		};

		// Make a triangle shape pointing up on the bottom
		altLines = new float[] {
			0.0f, bridge.charHeight, bridge.charWidth / 2.0f, (bridge.charHeight / 3.0f) * 2.0f,
			bridge.charWidth / 2.0f, (bridge.charHeight / 3.0f) * 2.0f, bridge.charWidth, bridge.charHeight,
			bridge.charWidth, bridge.charHeight, 0.0f, bridge.charHeight,
		};

		// Make a triangle shape pointing right in the middle
		ctrlLines = new float[] {
			0.0f, bridge.charHeight / 4.0f, bridge.charWidth, bridge.charHeight / 2.0f,
			bridge.charWidth, bridge.charHeight / 2.0f, 0.0f, (bridge.charHeight / 3.0f) * 2.0f,
			0.0f, (bridge.charHeight / 4.0f) * 3.0f, 0.0f, bridge.charHeight / 4.0f,
		};
	}

	@Override
	public void onDraw(Canvas canvas) {
		if(bridge.bitmap != null) {
			// draw the bitmap
			bridge.onDraw();

			// draw the bridge bitmap if it exists
			canvas.drawBitmap(bridge.bitmap, 0, 0, paint);

			// also draw cursor if visible
			if(bridge.buffer.isCursorVisible()) {
				int x = bridge.buffer.getCursorColumn() * bridge.charWidth;
				int y = (bridge.buffer.getCursorRow()
						+ bridge.buffer.screenBase - bridge.buffer.windowBase)
						* bridge.charHeight;

				// Save the current clip and translation
				canvas.save();

				canvas.translate(x, y);
				canvas.clipRect(0, 0, bridge.charWidth, bridge.charHeight);
				canvas.drawPaint(cursorPaint);

				int metaState = bridge.getMetaState();
				Log.d("cursor", "Meta state: " + metaState);
				if ((metaState & TerminalBridge.META_SHIFT_ON) == TerminalBridge.META_SHIFT_ON)
					canvas.drawLines(shiftLines, cursorPaint);
				if ((metaState & TerminalBridge.META_ALT_ON) == TerminalBridge.META_ALT_ON)
					canvas.drawLines(altLines, cursorPaint);
				if ((metaState & TerminalBridge.META_CTRL_ON) == TerminalBridge.META_CTRL_ON)
					canvas.drawLines(ctrlLines, cursorPaint);

				// Restore previous clip region
				canvas.restore();
			}

			// draw any highlighted area
			if (bridge.isSelectingForCopy()) {
				SelectionArea area = bridge.getSelectionArea();
				canvas.drawRect(
					area.getLeft() * bridge.charWidth,
					area.getTop() * bridge.charHeight,
					(area.getRight() + 1) * bridge.charWidth,
					(area.getBottom() + 1) * bridge.charHeight,
					cursorPaint
				);
			}
		}
	}

	public void notifyUser(String message) {
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
}
