package org.theb.ssh;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.mud.terminal.SoftFont;
import de.mud.terminal.VDUBuffer;
import de.mud.terminal.VDUDisplay;
import de.mud.terminal.vt320;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PixelXorXfermode;
import android.graphics.Typeface;
import android.graphics.Bitmap.Config;
import android.graphics.Paint.FontMetricsInt;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

public class JTATerminalView extends View implements VDUDisplay, Terminal, Runnable {
	private Paint paint;
	private Paint cursorPaint;
	
	private Canvas canvas;
	private Bitmap bitmap;
	
	protected vt320 emulation;
	private VDUBuffer buffer;
	
	private InputStream in;
	private OutputStream out;

	private String encoding = "ASCII";
	private SoftFont sf = new SoftFont();
	
	private Thread reader = null;
	
	private int charWidth;
	private int charHeight;
	private int charDescent;
	
	private int xoffset = 0;
	private int yoffset = 0;
	
	private int color[] = {
			Color.BLACK,
			Color.RED,
			Color.GREEN,
			Color.YELLOW,
			Color.BLUE,
			Color.MAGENTA,
			Color.CYAN,
			Color.WHITE,
	};
	
	private final static int COLOR_FG_STD = 7;
	private final static int COLOR_BG_STD = 0;
	
	public JTATerminalView(Context context) {
		super(context);
		
		paint = new Paint();
		paint.setAntiAlias(true);
		
		cursorPaint = new Paint();
		cursorPaint.setAntiAlias(true);
		cursorPaint.setColor(darken(color[COLOR_FG_STD]));
		cursorPaint.setXfermode(new PixelXorXfermode(color[COLOR_BG_STD]));
		
		setFont(Typeface.MONOSPACE, 10);

		emulation = new vt320() {
			public void write(byte[] b) {
				try {
					JTATerminalView.this.write(b);
				} catch (IOException e) {
					Log.e("SSH", "couldn't write" + b.toString());
					reader = null;
				}
			}
			
			public void sendTelnetCommand(byte cmd) {
				// TODO: implement telnet command sending
			}
			
			public void setWindowSize(int c, int r) {
				// TODO: implement window sizing
			}
		};
		
		setVDUBuffer(emulation);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (bitmap != null) {
			canvas.drawBitmap(bitmap, 0, 0, null);
			
			if (buffer.isCursorVisible() &&
					(buffer.screenBase + buffer.getCursorRow() >= buffer.windowBase &&
					buffer.screenBase + buffer.getCursorRow() < buffer.windowBase + buffer.height)) {
				int x = buffer.getCursorColumn() * charWidth + xoffset;
				int y = (buffer.getCursorRow() + buffer.screenBase - buffer.windowBase) * charHeight + yoffset;
				canvas.drawRect(x, y, x + charWidth, y + charHeight, cursorPaint);
			}
		}
	}
 
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		Log.d("SSH/TerminalView", "onSizeChanged called");
		Bitmap newBitmap = Bitmap.createBitmap(w, h, Config.ARGB_8888);
		Canvas newCanvas = new Canvas();
		
		newCanvas.setBitmap(newBitmap);
		
		if (bitmap != null)
			newCanvas.drawBitmap(bitmap, 0, 0, paint);
		
		bitmap = newBitmap;
		canvas = newCanvas;
		
		setSize(w, h);
		
		// Make sure the buffer is in the center of the screen.
		xoffset = (getWidth() - buffer.width * charWidth) / 2;
		yoffset = (getHeight() - buffer.height * charHeight) / 2;
	}
	
	private void setSize(int w, int h) {
		int termWidth = w / charWidth;
		int termHeight = h / charHeight;
		
		buffer.setScreenSize(termWidth, buffer.height = termHeight, true);
	}

	private void setFont(Typeface typeface, int size) {
		paint.setTypeface(typeface);
		paint.setTextSize(size);
		
		FontMetricsInt fm = paint.getFontMetricsInt();
		
		charDescent = fm.descent;
		
		float[] widths = new float[1];
		paint.getTextWidths("X", widths);
		charWidth = (int)widths[0];
		
		charHeight = Math.abs(fm.top) + Math.abs(fm.descent);
	}
	
	public void write(byte[] b) throws IOException {
		Log.e("SSH/JTATerm/write", "Trying to write" + b.toString());
		out.write(b);
	}
	
	public int getColumnCount() {
		return buffer.width;
	}

	public int getRowCount() {
		return buffer.height;
	}

	public InputStream getInput() {
		return in;
	}

	public byte[] getKeyCode(int keyCode, int meta) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_ENTER:
			emulation.keyTyped(vt320.KEY_ENTER, ' ', meta);
			break;
		case KeyEvent.KEYCODE_DPAD_LEFT:
			emulation.keyPressed(vt320.KEY_LEFT, ' ', meta);
			break;
		case KeyEvent.KEYCODE_DPAD_UP:
			emulation.keyPressed(vt320.KEY_UP, ' ', meta);
			break;
		case KeyEvent.KEYCODE_DPAD_DOWN:
			emulation.keyPressed(vt320.KEY_DOWN, ' ', meta) ;
			break;
		case KeyEvent.KEYCODE_DPAD_RIGHT:
			emulation.keyPressed(vt320.KEY_RIGHT, ' ', meta);
			break;
		}
		return null;
	}

	public OutputStream getOutput() {
		return out;
	}

	private int darken(int color) {
		return Color.argb(0xFF,
			(int)(Color.red(color) * 0.8),
			(int)(Color.green(color) * 0.8),
			(int)(Color.blue(color) * 0.8)
		);
	}
	
	/* Not used.
	private int brighten(int color) {
		return Color.argb(0xFF,
			(int)(Color.red(color) * 1.2),
			(int)(Color.green(color) * 1.2),
			(int)(Color.blue(color) * 1.2)
		);
	}
	*/
	
	public void redraw() {
		// Draw the mouse-selection
		//int selectStartLine = selectBegin.y - buffer.windowBase;
		//int selectEndLine = selectEnd.y - buffer.windowBase;
		
		int fg, bg;

		int lines = 0;
		long time = System.currentTimeMillis() + 0;

		// paint.setColor(color[COLOR_BG_STD]);
		// canvas.drawRect(0, 0, bitmap.getWidth(), bitmap.getHeight(), paint);
		// paint.setColor(color[COLOR_FG_STD]);

				
		for (int l = 0; l < buffer.height; l++) {
			// Check to see if the entire buffer is dirty or if this line is dirty.
			// If neither is dirty, continue.
			if (!buffer.update[0] && !buffer.update[l + 1]) continue;
			buffer.update[l + 1] = false;
			
			lines++;

			// assume that we can blindly dump the terminal string
			// canvas.drawText(buffer.charArray[buffer.windowBase + l],
			// 0, buffer.charArray[buffer.windowBase + l].length,
			// 0 * charWidth + xoffset,
			// (l + 1) * charHeight - charDescent + yoffset,
			// paint);

			
			for (int c = 0; c < buffer.width; c++) {
				int addr = 0;
				int currAttr = buffer.charAttributes[buffer.windowBase + l][c];
				
				fg = darken(color[COLOR_FG_STD]);
				bg = darken(color[COLOR_BG_STD]);
				
				// Check if foreground color attribute is set.
				if ((currAttr & VDUBuffer.COLOR_FG) != 0)
					fg = (color[((currAttr & VDUBuffer.COLOR_FG) >> VDUBuffer.COLOR_FG_SHIFT) - 1]);

				// Check if background color attribute is set.
				if ((currAttr & VDUBuffer.COLOR_BG) != 0)
					bg = (darken(color[((currAttr & VDUBuffer.COLOR_BG) >> VDUBuffer.COLOR_BG_SHIFT) - 1]));

				// Check if bold attribute is set.
				paint.setFakeBoldText((currAttr & VDUBuffer.BOLD) != 0);
				
				if ((currAttr & VDUBuffer.LOW) != 0)
					fg = darken(fg);
				
				// Support character inversion by swapping background and foreground color.
				if ((currAttr & VDUBuffer.INVERT) != 0) {
					int swapc = bg;
					bg = fg;
					fg = swapc;
				}
				
				// If this character is in the special font, print it and continue to the next character.
				// We can't use the optimization below for special characters.
//				if (sf.inSoftFont(buffer.charArray[buffer.windowBase + l][c])) {
//					// Clear out the space where the character will be printed.
//					paint.setColor(bg);
//					canvas.drawRect(c * charWidth + xoffset, l * charHeight + yoffset,
//							c * (charWidth + 1) + xoffset, (l+1) * charHeight + yoffset, paint);
//					paint.setColor(fg);
//					
//					// FIXME: this won't work since we're not calling drawText()
//					paint.setUnderlineText((currAttr & VDUBuffer.UNDERLINE) != 0);
//					
//					// Draw the text if it's not invisible.
//					if ((currAttr & VDUBuffer.INVISIBLE) == 0)
//						sf.drawChar(canvas, paint, buffer.charArray[buffer.windowBase + l][c], xoffset + c * charWidth, l * charHeight + yoffset, charWidth, charHeight);
//					continue;
//				}
				
				// Determine the amount of continuous characters with the same settings and print them all at once.
//				while ((c + addr < buffer.width) &&
//						((buffer.charArray[buffer.windowBase + l][c + addr] < ' ') ||
//								(buffer.charAttributes[buffer.windowBase + l][c + addr] == currAttr)) &&
//								!sf.inSoftFont(buffer.charArray[buffer.windowBase + l][c + addr])) {
//					if (buffer.charArray[buffer.windowBase + l][c + addr] < ' ') {
//						buffer.charArray[buffer.windowBase + l][c + addr] = ' ';
//						buffer.charAttributes[buffer.windowBase + l][c + addr] = 0;
//						continue;
//					}
//					addr++;
//				}
				
				while(c + addr < buffer.width && buffer.charAttributes[buffer.windowBase + l][c + addr] == currAttr) {
					addr++;
				}
				
				// Clear the background in preparation for writing text.
				paint.setColor(bg);
				canvas.drawRect(c * charWidth + xoffset, l * charHeight + yoffset,
						(c + addr) * charWidth + xoffset, (l+1) * charHeight + yoffset, paint);
				paint.setColor(fg);
				
				// Check for the underline attribute and set the brush accordingly.
				paint.setUnderlineText((currAttr & VDUBuffer.UNDERLINE) != 0);
				
				// Write the text string starting at 'c' for 'addr' number of characters.
				if ((currAttr & VDUBuffer.INVISIBLE) == 0)
					canvas.drawText(buffer.charArray[buffer.windowBase + l],
							c, addr,
							c * charWidth + xoffset,
							(l + 1) * charHeight - charDescent + yoffset,
							paint);
				
				// Advance to the next text block with different characteristics.
				c += addr - 1;
			}
		}
		
		buffer.update[0] = false;
		
		time = System.currentTimeMillis() - time;
		Log.d("redraw", "redraw called and updated lines=" + lines + " taking ms=" + time);

		postInvalidate();
	}

	public void updateScrollBar() {
		// TODO Auto-generated method stub
	}

	public void start(InputStream in, OutputStream out) {
		this.in = in;
		this.out = out;
		
		reader = new Thread(this);
		reader.start();
	}

	public VDUBuffer getVDUBuffer() {
		return buffer;
	}

	public void setVDUBuffer(VDUBuffer buffer) {
		this.buffer = buffer;
		buffer.setDisplay(this);
	}

	public void run() {
		byte[] b = new byte[256];
		int n = 0;
		while (n >= 0)
			try {
				n = in.read(b);
				if (n > 0) emulation.putString(new String(b, 0, n, encoding));
				redraw();
			} catch (IOException e) {
				reader = null;
				break;
			}
	}
}
