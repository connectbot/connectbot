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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelXorXfermode;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

/**
 * User interface {@link View} for showing a TerminalBridge in an
 * {@link Activity}. Handles drawing bitmap updates and passing keystrokes down
 * to terminal.
 * 
 * @author jsharkey
 */
public class TerminalView extends View {

	protected final Context context;
	protected final TerminalBridge bridge;
	protected final Paint paint;
	protected final Paint cursorPaint;

	public TerminalView(Context context, TerminalBridge bridge) {
		super(context);
		
		this.context = context;
		this.bridge = bridge;
		this.paint = new Paint();
		
		this.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		this.setFocusable(true);
		this.setFocusableInTouchMode(true);
		
		this.cursorPaint = new Paint();
		this.cursorPaint.setColor(bridge.color[bridge.COLOR_FG_STD]);
		this.cursorPaint.setXfermode(new PixelXorXfermode(bridge.color[bridge.COLOR_BG_STD]));
		
		// connect our view up to the bridge
		this.setOnKeyListener(bridge);
		
		
	}
	
	public void destroy() {
		// tell bridge to destroy its bitmap
		this.bridge.parentDestroyed();
	}
	
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		super.onSizeChanged(w, h, oldw, oldh);
		this.bridge.parentChanged(this);
	}
	
	@Override
	public void onDraw(Canvas canvas) {
		if(this.bridge.bitmap != null) {
			
			// draw the bridge bitmap if it exists
			canvas.drawBitmap(this.bridge.bitmap, 0, 0, this.paint);
			
			// also draw cursor if visible
			if(this.bridge.buffer.isCursorVisible()) {
				int x = this.bridge.buffer.getCursorColumn() * this.bridge.charWidth;
				int y = (this.bridge.buffer.getCursorRow()
						+ this.bridge.buffer.screenBase - this.bridge.buffer.windowBase)
						* this.bridge.charHeight;
				
				canvas.drawRect(x, y, x + this.bridge.charWidth,
						y + this.bridge.charHeight, cursorPaint);
				
			}

		}
		
	}
	
}
