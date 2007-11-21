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
import android.graphics.Typeface;
import android.graphics.Paint.FontMetricsInt;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;

public class JTATerminalView extends View implements VDUDisplay, Terminal, Runnable {
	private Paint paint;
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
	
	private int termWidth;
	private int termHeight;
	
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
		setFont(Typeface.MONOSPACE, 8);

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
		emulation.setDisplay(this);
	}

	@Override
	protected void onDraw(Canvas canvas) {
		if (bitmap != null) {
			canvas.drawBitmap(bitmap, 0, 0, null);
			/*
			if (charHeight > 0 && y > charHeight) {
				// Invert pixels for cursor position.
				Bitmap cursor = Bitmap.createBitmap(mBitmap, x, y - mCharHeight, mCharWidth, mCharHeight);
				for (int cy = 0; cy < mCharHeight; cy++)
					for (int cx = 0; cx < mCharWidth; cx++)
						cursor.setPixel(cx, cy, (~cursor.getPixel(cx, cy) & 0xFFFFFFFF) | 0xFF000000);
				canvas.drawBitmap(cursor, x, y - mCharHeight, null);
				cursor = null;
			}
			*/
		}
	}
 
	@Override
	protected void onSizeChanged(int w, int h, int oldw, int oldh) {
		Log.d("SSH/TerminalView", "onSizeChanged called");
		Bitmap newBitmap = Bitmap.createBitmap(w, h, false);
		Canvas newCanvas = new Canvas();
		
		newCanvas.setDevice(newBitmap);
		
		if (bitmap != null)
			newCanvas.drawBitmap(bitmap, 0, 0, paint);
		
		bitmap = newBitmap;
		canvas = newCanvas;
		
		setSize(w, h);
	}
	
	private void setSize(int w, int h) {
		termWidth = w / charWidth;
		termHeight = h / charHeight;
		
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
		return termWidth;
	}

	public InputStream getInput() {
		return in;
	}

	public byte[] getKeyCode(int keyCode, int meta) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_NEWLINE:
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

	public int getRowCount() {
		return termHeight;
	}

	private int darken(int color) {
		return Color.argb(0xFF,
			(int)(Color.red(color) * 0.8),
			(int)(Color.green(color) * 0.8),
			(int)(Color.blue(color) * 0.8)
		);
	}
	
	/*
	private int brighten(int color) {
		return Color.argb(0xFF,
			(int)(Color.red(color) * 1.2),
			(int)(Color.green(color) * 1.2),
			(int)(Color.blue(color) * 1.2)
		);
	}
	*/
	
	public void redraw() {
		// Make sure the buffer is in the center of the screen.
		int xoffset = (getWidth() - buffer.width * charWidth) / 2;
		int yoffset = (getHeight() - buffer.height * charHeight) / 2;
		
		// Draw the mouse-selection
		//int selectStartLine = selectBegin.y - buffer.windowBase;
		//int selectEndLine = selectEnd.y - buffer.windowBase;
		
		int fg, bg;
		
		for (int l = 0; l < buffer.height; l++) {
			if (!buffer.update[0] && !buffer.update[l + 1]) continue;
			
			for (int c = 0; c < buffer.width; c++) {
				int addr = 0;
				int currAttr = buffer.charAttributes[buffer.windowBase + l][c];
				
				fg = darken(color[COLOR_FG_STD]);
				bg = darken(color[COLOR_BG_STD]);
				
				if ((currAttr & VDUBuffer.COLOR_FG) != 0)
					fg = darken(color[((currAttr & VDUBuffer.COLOR_FG) >> VDUBuffer.COLOR_FG_SHIFT) - 1]);
				if ((currAttr & VDUBuffer.COLOR_BG) != 0)
					bg = darken(darken(color[((currAttr & VDUBuffer.COLOR_BG) >> VDUBuffer.COLOR_BG_SHIFT) - 1]));
				paint.setFakeBoldText((currAttr & VDUBuffer.BOLD) != 0);
				
				if ((currAttr & VDUBuffer.LOW) != 0)
					fg = darken(fg);
				
				if ((currAttr & VDUBuffer.INVERT) != 0) {
					int swapc = bg;
					bg = fg;
					fg = swapc;
				}
				
				// If this character is in the special font, print it and continue to the next character.
				if (sf.inSoftFont(buffer.charArray[buffer.windowBase + l][c])) {
					paint.setColor(bg);
					canvas.drawRect(c * charWidth + xoffset, l * charHeight + yoffset,
							c * (charWidth + 1) + xoffset, (l+1) * charHeight + yoffset, paint);
					paint.setColor(fg);
					paint.setUnderlineText((currAttr & VDUBuffer.UNDERLINE) != 0);
					if ((currAttr & VDUBuffer.INVISIBLE) == 0)
						sf.drawChar(canvas, paint, buffer.charArray[buffer.windowBase + l][c], xoffset + c * charWidth, l * charHeight + yoffset, charWidth, charHeight);
					continue;
				}
				
				// Determine the amount of continuous characters with the same settings and print them all at once.
				while ((c + addr < buffer.width) &&
						((buffer.charArray[buffer.windowBase + l][c + addr] < ' ') ||
								(buffer.charAttributes[buffer.windowBase + l][c + addr] == currAttr)) &&
								!sf.inSoftFont(buffer.charArray[buffer.windowBase + l][c + addr])) {
					if (buffer.charArray[buffer.windowBase + l][c + addr] < ' ') {
						buffer.charArray[buffer.windowBase + l][c + addr] = ' ';
						buffer.charAttributes[buffer.windowBase + l][c + addr] = 0;
						continue;
					}
					addr++;
				}
				
				paint.setColor(bg);
				canvas.drawRect(c * charWidth + xoffset, l * charHeight + yoffset,
						addr * (charWidth + 1) + xoffset, (l+1) * charHeight + yoffset, paint);
				paint.setColor(fg);
				
				paint.setUnderlineText((currAttr & VDUBuffer.UNDERLINE) != 0);
				if ((currAttr & VDUBuffer.INVISIBLE) == 0)
					canvas.drawText(buffer.charArray[buffer.windowBase + l],
							c, addr,
							c * charWidth + xoffset,
							(l + 1) * charHeight - charDescent + yoffset,
							paint);
				
				c += addr - 1;
			}
		}
		
		buffer.update[0] = false;
		
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
