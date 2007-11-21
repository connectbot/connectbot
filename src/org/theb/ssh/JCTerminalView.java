/*
 * Copyright (C) 2007 Kenny Root (kenny at the-b.org)
 * 
 * This file is part of Connectbot.
 *
 *  Connectbot is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Connectbot is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Connectbot.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.theb.ssh;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelXorXfermode;
import android.graphics.Typeface;
import android.graphics.Paint.FontMetricsInt;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

import com.jcraft.jcterm.Emulator;
import com.jcraft.jcterm.EmulatorVT100;
import com.jcraft.jcterm.Term;

public class JCTerminalView extends View implements Term, Terminal {
	private final Paint mPaint;
	private Bitmap mBitmap;
	private Canvas mCanvas;

	private final Paint mCursorPaint;
	
	private Emulator emulator = null;
	
	private boolean mBold = false;
	private boolean mUnderline = false;
	private boolean mReverse = false;
	
	private int mDefaultForeground = Color.WHITE;
	private int mDefaultBackground = Color.BLACK;
	private int mForeground = Color.WHITE;
	private int mBackground = Color.BLACK;
	
	private boolean mAntialias = true;
	
	private int mTermWidth = 80;
	private int mTermHeight = 24;
	
	private int mCharHeight;
	private int mCharWidth;
	private int mDescent;
	
	
	// Cursor location
	private int x = 0;
	private int y = 0;
	
	private final Object[] mColors = {Color.BLACK, Color.RED, Color.GREEN, Color.YELLOW,
			Color.BLUE, Color.MAGENTA, Color.CYAN, Color.WHITE};
	
	public JCTerminalView(Context c) {
		super(c);
		mPaint = new Paint();
		mPaint.setAntiAlias(mAntialias);
		mPaint.setColor(mDefaultForeground);
		
		mCursorPaint = new Paint();
		mCursorPaint.setAntiAlias(mAntialias);
		mCursorPaint.setColor(mDefaultForeground);
		mCursorPaint.setXfermode(new PixelXorXfermode(mDefaultBackground));
		
		setFont(Typeface.MONOSPACE);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (mBitmap != null) {
			canvas.drawBitmap(mBitmap, 0, 0, null);
			
			if (mCharHeight > 0 && y > mCharHeight) {
				// Invert pixels for cursor position.
				canvas.drawRect(x, y - mCharHeight, x + mCharWidth, y, mCursorPaint);
			}
		}
	}
 
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		Log.d("SSH/TerminalView", "onSizeChanged called");
		Bitmap newBitmap = Bitmap.createBitmap(w, h, false);
		Canvas newCanvas = new Canvas();
		
		newCanvas.setDevice(newBitmap);
		
		if (mBitmap != null)
			newCanvas.drawBitmap(mBitmap, 0, 0, mPaint);
		
		mBitmap = newBitmap;
		mCanvas = newCanvas;
		
		setSize(w, h);
	}
	
	private void setSize(int w, int h) {
		int column = w / getCharWidth();
		int row = h / getCharHeight();
		
		mTermWidth = column;
		mTermHeight = row;
		
		if (emulator != null)
			emulator.reset();
		
		clear_area(0, 0, w, h);
		
		// TODO: finish this method
	}

	private void setFont(Typeface typeface) {
		mPaint.setTypeface(typeface);
		mPaint.setTextSize(8);
		FontMetricsInt fm = mPaint.getFontMetricsInt();
		mDescent = fm.descent;
		
		float[] widths = new float[1];
		mPaint.getTextWidths("X", widths);
		mCharWidth = (int)widths[0];
		
		// Is this right?
		mCharHeight = Math.abs(fm.top) + Math.abs(fm.descent);
		Log.d("SSH", "character height is " + mCharHeight);
		// mCharHeight += mLineSpace * 2;
		// mDescent += mLineSpace;
	}

	public void beep() {
		// TODO Auto-generated method stub
		
	}

	public void clear() {
		mPaint.setColor(getBackgroundColor());
		mCanvas.drawRect(0, 0, mCanvas.getBitmapWidth(),
				mCanvas.getBitmapHeight(), mPaint);
		mPaint.setColor(getForegroundColor());
	}

	private int getBackgroundColor() {
		if (mReverse)
			return mForeground;
		return mBackground;
	}
	
	private int getForegroundColor() {
		if (mReverse)
			return mBackground;
		return mForeground;
	}

	public void clear_area(int x1, int y1, int x2, int y2) {
		mPaint.setColor(getBackgroundColor());
		if (mCanvas != null)
			mCanvas.drawRect(x1, y1, x2, y2, mPaint);
		mPaint.setColor(getForegroundColor());
	}

	public void drawBytes(byte[] buf, int s, int len, int x, int y) {
		String chars = null;
		try {
			chars = new String(buf, "ASCII");
			drawString(chars.substring(s, s+len), x, y);
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			Log.e("SSH", "Can't convert bytes to ASCII");
		}
	}

	public void drawString(String str, int x, int y) {
		mPaint.setFakeBoldText(mBold);
		mPaint.setUnderlineText(mUnderline);
		mCanvas.drawText(str, x, y - mDescent, mPaint);
	}

	public void draw_cursor() {
		postInvalidate();
	}

	public int getCharHeight() {
		return mCharHeight;
	}

	public int getCharWidth() {
		return mCharWidth;
	}

	public Object getColor(int index) {
		if (mColors == null || index < 0 || mColors.length <= index)
			return null;
		return mColors[index];
	}

	public int getColumnCount() {
		return mTermWidth;
	}

	public int getRowCount() {
		return mTermHeight;
	}

	public int getTermHeight() {
		return mTermHeight * mCharHeight;
	}

	public int getTermWidth() {
		return mTermWidth * mCharWidth;
	}

	public void redraw(int x, int y, int width, int height) {
		//invalidate(x, y, x+width, y+height);
		postInvalidate();
	}

	public void resetAllAttributes() {
		mBold = false;
		mUnderline = false;
		mReverse = false;
		
		mBackground = mDefaultBackground;
		mForeground = mDefaultForeground;
		
		if (mPaint != null)
			mPaint.setColor(mForeground);
	}

	public void scroll_area(int x, int y, int w, int h, int dx, int dy) {
		// TODO: make scrolling more efficient (memory-wise)
		mCanvas.drawBitmap(Bitmap.createBitmap(mBitmap, x, y, w, h), x+dx, y+dy, null);
	}

	private int toColor(Object o) {
		if (o instanceof Integer) {
			return ((Integer)o).intValue();
		}
		
		if (o instanceof String) {
			return Color.parseColor((String)o);
		}
		
		return Color.WHITE;
	}
	
	public void setBackGround(Object background) {
		mBackground = toColor(background);
	}

	public void setBold() {
		mBold = true;
	}

	public void setCursor(int x, int y) {
		// Make sure we don't go outside the bounds of the window.
		this.x = Math.max(
				Math.min(x, getWidth() - mCharWidth),
				0);
		this.y = Math.max(
				Math.min(y, getHeight()),
				mCharHeight);
	}

	public void setDefaultBackGround(Object background) {
		mDefaultBackground = toColor(background);
	}

	public void setDefaultForeGround(Object foreground) {
		mDefaultForeground = toColor(foreground);
	}

	public void setForeGround(Object foreground) {
		mForeground = toColor(foreground);
	}

	public void setReverse() {
		mReverse = true;
		if (mPaint != null)
			mPaint.setColor(getForegroundColor());
	}

	public void setUnderline() {
		mUnderline = true;
	}

	public void start(InputStream in, OutputStream out) {
		emulator = new EmulatorVT100(this, in);
		emulator.reset();
		emulator.start();
		
		clear();
	}

	public byte[] getKeyCode(int keyCode, int meta) {
		if (keyCode == KeyEvent.KEYCODE_NEWLINE)
			return emulator.getCodeENTER();
		else if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT)
			return emulator.getCodeLEFT();
		else if (keyCode == KeyEvent.KEYCODE_DPAD_UP)
			return emulator.getCodeUP();
		else if (keyCode == KeyEvent.KEYCODE_DPAD_DOWN)
			return emulator.getCodeDOWN();
		else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT)
			return emulator.getCodeRIGHT();
		else
			return null;
	}
}
