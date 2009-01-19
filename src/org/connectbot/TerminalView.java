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

import org.connectbot.service.TerminalBridge;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelXorXfermode;
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
	
	private Toast notification = null;
	private String lastNotification = null;
	
	protected int top = -1, bottom = -1, left = -1, right = -1;
	
	public void resetSelected() {
		top = -1;
		bottom = -1;
		left = -1;
		right = -1;
	}

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
				
				canvas.drawRect(x, y, x + bridge.charWidth,
						y + bridge.charHeight, cursorPaint);
				
			}
			
			// draw any highlighted area
			if(top >= 0 && bottom >= 0 && left >= 0 && right >= 0) {
				canvas.drawRect(left * bridge.charWidth, top * bridge.charHeight,
					right * bridge.charWidth, bottom * bridge.charHeight, cursorPaint);
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
