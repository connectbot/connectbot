package org.connectbot.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.theb.ssh.JTATerminalView;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.Bitmap.Config;
import android.graphics.Paint.FontMetricsInt;
import android.util.Log;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.View.OnKeyListener;

import com.trilead.ssh2.Session;
import com.trilead.ssh2.Connection;

import de.mud.terminal.VDUBuffer;
import de.mud.terminal.VDUDisplay;
import de.mud.terminal.vt320;


// provides a bridge between the mud buffer and a surfaceholder
public class TerminalBridge implements VDUDisplay, OnKeyListener {
	
	public final Connection connection;
	public final Session session;
	public final String overlay;
	
	public final static int TERM_WIDTH_CHARS = 80,
		TERM_HEIGHT_CHARS = 24,
		DEFAULT_FONT_SIZE = 10;
	
	public final static String ENCODING = "ASCII";
	
	public final OutputStream stdin;
	public final InputStream stdout;
	
	public final Paint defaultPaint;
	
	public final Thread relay;
	
	public View parent = null;
	public Bitmap bitmap = null;
	public Canvas canvas = new Canvas();
	public VDUBuffer buffer = null;
	
	public TerminalBridge(Connection connection, String overlay, String emulation, int scrollback) throws Exception {
		// create a terminal bridge from an SSH connection over to a SurfaceHolder
		// will open a new session and handle rendering to the Surface if present
		
		try {
			this.connection = connection;
			this.session = connection.openSession();
			this.session.requestPTY(emulation, 0, 0, 0, 0, null); // previously tried vt100, xterm, but "screen" works the best
			this.session.startShell();
			
			this.overlay = overlay;
			
			// grab stdin/out from newly formed session
			this.stdin = this.session.getStdin();
			this.stdout = this.session.getStdout();
			
			// create our default paint
			this.defaultPaint = new Paint();
			this.defaultPaint.setAntiAlias(true);
			this.defaultPaint.setTypeface(Typeface.MONOSPACE);
			this.setFontSize(DEFAULT_FONT_SIZE);
			
			// create terminal buffer and handle outgoing data
			// this is probably status reply information
			this.buffer = new vt320() {
				public void write(byte[] b) {
					try {
						//Log.d("STDIN", new String(b));
						TerminalBridge.this.stdin.write(b);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				public void sendTelnetCommand(byte cmd) {
				}

				public void setWindowSize(int c, int r) {
				}
			};
			this.scrollback = scrollback;
			this.buffer.setScreenSize(TERM_WIDTH_CHARS, TERM_HEIGHT_CHARS, true);
			this.buffer.setBufferSize(scrollback);
			this.buffer.setDisplay(this);
			
			// create thread to relay incoming connection data to buffer
			this.relay = new Thread(new Runnable() {
				public void run() {
					byte[] b = new byte[256];
					int n = 0;
					while(n >= 0) {
						try {
							n = TerminalBridge.this.stdout.read(b);
							if(n > 0) {
								// pass along data to buffer, then redraw any results
								((vt320)TerminalBridge.this.buffer).putString(new String(b, 0, n, ENCODING));
								TerminalBridge.this.redraw();
							}
						} catch (IOException e) {
							e.printStackTrace();
							break;
						}
					}
				}
			});
			this.relay.start();
			
		} catch(Exception e) {
			throw e;
		}
		
		for(int i = 0; i < color.length; i++)
			this.darkerColor[i] = darken(color[i]);

	}
	
	public void dispose() {
		this.session.close();
		this.connection.close();
	}
	
	KeyCharacterMap keymap = KeyCharacterMap.load(KeyCharacterMap.BUILT_IN_KEYBOARD);

	public boolean onKey(View v, int keyCode, KeyEvent event) {
		// pass through any keystrokes to output stream
		if(event.getAction() == KeyEvent.ACTION_UP) return false;
		try {

			// check for resizing keys
			if(keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
				this.setFontSize(this.fontSize + 2);
				return true;
			} else if(keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
				this.setFontSize(this.fontSize - 2);
				return true;
			}
			
			// print normal keys
			if(keymap.isPrintingKey(keyCode) || keyCode == KeyEvent.KEYCODE_SPACE) {
		    	int key = keymap.get(keyCode, event.getMetaState());
		    	this.stdin.write(key);
				return true;
			}
			
			// look for special chars
			switch(keyCode) {
				case KeyEvent.KEYCODE_DEL: stdin.write(0x08); return true;
				case KeyEvent.KEYCODE_ENTER: ((vt320)buffer).keyTyped(vt320.KEY_ENTER, ' ', event.getMetaState()); return true;
				case KeyEvent.KEYCODE_DPAD_LEFT: ((vt320)buffer).keyPressed(vt320.KEY_LEFT, ' ', event.getMetaState()); return true;
				case KeyEvent.KEYCODE_DPAD_UP: ((vt320)buffer).keyPressed(vt320.KEY_UP, ' ', event.getMetaState()); return true;
				case KeyEvent.KEYCODE_DPAD_DOWN: ((vt320)buffer).keyPressed(vt320.KEY_DOWN, ' ', event.getMetaState()); return true;
				case KeyEvent.KEYCODE_DPAD_RIGHT: ((vt320)buffer).keyPressed(vt320.KEY_RIGHT, ' ', event.getMetaState()); return true;
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		return false;
	}

	public int charWidth = -1,
		charHeight = -1,
		charDescent = -1;
	
	public float fontSize = -1;
	
	public int scrollback = 120;
	
	public void setFontSize(float size) {
		this.defaultPaint.setTextSize(size);
		this.fontSize = size;
		
		// read new metrics to get exact pixel dimensions
		FontMetricsInt fm = this.defaultPaint.getFontMetricsInt();
		this.charDescent = fm.descent;
		
		float[] widths = new float[1];
		this.defaultPaint.getTextWidths("X", widths);
		this.charWidth = (int)widths[0];
		this.charHeight = Math.abs(fm.top) + Math.abs(fm.descent);
		
		// refresh any bitmap with new font size
		if(this.parent != null)
			this.parentChanged(this.parent);
	}
	
	public boolean fullRedraw = false;
	
	public synchronized void parentChanged(View parent) {
		
		this.parent = parent;
		int width = parent.getWidth();
		int height = parent.getHeight();
		
		// reallocate new bitmap if needed
		boolean newBitmap = (this.bitmap == null);
		if(this.bitmap != null)
			newBitmap = (this.bitmap.getWidth() != width || this.bitmap.getHeight() != height);
		if(newBitmap) {
			this.bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
			this.canvas.setBitmap(this.bitmap);
		}
		
		// clear out any old buffer information
		this.defaultPaint.setColor(Color.BLACK);
		this.canvas.drawRect(0, 0, width, height, this.defaultPaint);

		// recalculate buffer size and update buffer
		int termWidth = width / charWidth;
		int termHeight = height / charHeight;
		
		try {
			buffer.setScreenSize(termWidth, termHeight, true);
			session.resizePTY(termWidth, termHeight);
		} catch(Exception e) {
			e.printStackTrace();
		}
		
		// force full redraw with new buffer size
		this.fullRedraw = true;
		this.redraw();

		Log.d(this.getClass().toString(), "parentChanged() now width=" + termWidth + ", height=" + termHeight);
	}
	
	public synchronized void parentDestroyed() {
		this.parent = null;
		this.bitmap = null;
		this.canvas.setBitmap(null);
	}

	public void setVDUBuffer(VDUBuffer buffer) {
		this.buffer = buffer;
	}

	public VDUBuffer getVDUBuffer() {
		return buffer;
	}
	
	private int darken(int color) {
		return Color.argb(0xFF,
			(int)(Color.red(color) * 0.8),
			(int)(Color.green(color) * 0.8),
			(int)(Color.blue(color) * 0.8)
		);
	}
	
	private int color[] = { Color.BLACK, Color.RED, Color.GREEN, Color.YELLOW,
		Color.BLUE, Color.MAGENTA, Color.CYAN, Color.WHITE, };
	
	private int darkerColor[] = new int[color.length];
	
	private final static int COLOR_FG_STD = 7;
	private final static int COLOR_BG_STD = 0;
	
	public long lastDraw = 0;
	public long drawTolerance = 100;

	public synchronized void redraw() {
		// render our buffer only if we have a surface
		if(this.parent == null) return;
		
		long time = System.currentTimeMillis();
		int lines = 0;
		
		// only worry about rendering if its been awhile
		//if(time - lastDraw < drawTolerance) return;
		//lastDraw = time;

		int fg, bg;
		boolean entireDirty = buffer.update[0] || this.fullRedraw;
		
		// walk through all lines in the buffer
		for(int l = 0; l < buffer.height; l++) {
			
//			if(entireDirty)
//				Log.w("BUFFERDUMP", new String(buffer.charArray[l]));
			
			// check if this line is dirty and needs to be repainted
			// also check for entire-buffer dirty flags
			if(!entireDirty && !buffer.update[l + 1]) continue;
			lines++;
			
			// reset dirty flag for this line
			buffer.update[l + 1] = false;
			
			// lock this entire row as being dirty
			//Rect dirty = new Rect(0, l * charHeight, buffer.width * charWidth, (l + 1) * charHeight);
			
			// walk through all characters in this line
			for (int c = 0; c < buffer.width; c++) {
				int addr = 0;
				int currAttr = buffer.charAttributes[buffer.windowBase + l][c];

				// reset default colors
				fg = color[COLOR_FG_STD];
				bg = color[COLOR_BG_STD];
				
				// check if foreground color attribute is set
				if((currAttr & VDUBuffer.COLOR_FG) != 0)
					fg = color[((currAttr & VDUBuffer.COLOR_FG) >> VDUBuffer.COLOR_FG_SHIFT) - 1];

				// check if background color attribute is set
				if((currAttr & VDUBuffer.COLOR_BG) != 0)
					bg = darkerColor[((currAttr & VDUBuffer.COLOR_BG) >> VDUBuffer.COLOR_BG_SHIFT) - 1];
				
				// support character inversion by swapping background and foreground color
				if ((currAttr & VDUBuffer.INVERT) != 0) {
					int swapc = bg;
					bg = fg;
					fg = swapc;
				}
				
				// if black-on-black, try correcting to grey
				if(fg == Color.BLACK && bg == Color.BLACK)
					fg = Color.GRAY;
				
				// correctly set bold and underlined attributes if requested
				this.defaultPaint.setFakeBoldText((currAttr & VDUBuffer.BOLD) != 0);
				this.defaultPaint.setUnderlineText((currAttr & VDUBuffer.UNDERLINE) != 0);
				
				// determine the amount of continuous characters with the same settings and print them all at once
				while(c + addr < buffer.width && buffer.charAttributes[buffer.windowBase + l][c + addr] == currAttr) {
					addr++;
				}
				
				// clear this dirty area with background color
				this.defaultPaint.setColor(bg);
				canvas.drawRect(c * charWidth, l * charHeight, (c + addr) * charWidth, (l + 1) * charHeight, this.defaultPaint);
				
				// write the text string starting at 'c' for 'addr' number of characters
				this.defaultPaint.setColor(fg);
				if((currAttr & VDUBuffer.INVISIBLE) == 0)
					canvas.drawText(buffer.charArray[buffer.windowBase + l], c,
						addr, c * charWidth, ((l + 1) * charHeight) - charDescent,
						this.defaultPaint);
				
				// advance to the next text block with different characteristics
				c += addr - 1;
			}

			// unlock this row and update
			//this.current.unlockCanvasAndPost(canvas);
		}
		
		// reset entire-buffer flags
		buffer.update[0] = false;
		this.fullRedraw = false;
		
		// dump out rendering statistics
		time = System.currentTimeMillis() - time;
		//Log.d(this.getClass().toString(), "redraw called and updated lines=" + lines + " taking ms=" + time);
		
		this.parent.postInvalidate();
		
	}

	public void updateScrollBar() {
		// TODO Auto-generated method stub
		
	}

	

}
