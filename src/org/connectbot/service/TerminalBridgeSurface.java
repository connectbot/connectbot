package org.connectbot.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.theb.ssh.JTATerminalView;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
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
public class TerminalBridgeSurface implements SurfaceHolder.Callback, VDUDisplay, OnKeyListener {
	
	public final Connection connection;
	public final Session session;
	
	public final static int TERM_WIDTH_CHARS = 80,
		TERM_HEIGHT_CHARS = 24,
		DEFAULT_FONT_SIZE = 10;
	
	public final static String ENCODING = "ASCII";
	
	public final OutputStream stdin;
	public final InputStream stdout;
	
	public final Paint defaultPaint;
	
	public final Thread relay;
	
	public SurfaceHolder current = null;
	public VDUBuffer buffer = null;
	
	public TerminalBridgeSurface(Connection connection) throws Exception {
		// create a terminal bridge from an SSH connection over to a SurfaceHolder
		// will open a new session and handle rendering to the Surface if present
		
		try {
			this.connection = connection;
			this.session = connection.openSession();
			this.session.requestPTY("xterm", TERM_WIDTH_CHARS, TERM_HEIGHT_CHARS, 0, 0, null);
			this.session.startShell();

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
						Log.d("STDIN", new String(b));
						TerminalBridgeSurface.this.stdin.write(b);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				public void sendTelnetCommand(byte cmd) {
				}

				public void setWindowSize(int c, int r) {
				}
			};
			this.buffer.setDisplay(this);
			
			// create thread to relay incoming connection data to buffer
			this.relay = new Thread(new Runnable() {
				public void run() {
					byte[] b = new byte[256];
					int n = 0;
					while(n >= 0) {
						try {
							n = TerminalBridgeSurface.this.stdout.read(b);
							if(n > 0) {
								// pass along data to buffer, then redraw any results
								((vt320)TerminalBridgeSurface.this.buffer).putString(new String(b, 0, n, ENCODING));
								TerminalBridgeSurface.this.redraw();
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

	}
	
	KeyCharacterMap keymap = KeyCharacterMap.load(KeyCharacterMap.BUILT_IN_KEYBOARD);

	public boolean onKey(View v, int keyCode, KeyEvent event) {
		// pass through any keystrokes to output stream
		if(event.getAction() == KeyEvent.ACTION_UP) return false;
		try {
	    	int key = keymap.get(keyCode, event.getMetaState());
			switch(keyCode) {
				case KeyEvent.KEYCODE_DEL: stdin.write(0x08); break;
				case KeyEvent.KEYCODE_ENTER: ((vt320)buffer).keyTyped(vt320.KEY_ENTER, ' ', event.getMetaState()); break;
				case KeyEvent.KEYCODE_DPAD_LEFT: ((vt320)buffer).keyPressed(vt320.KEY_LEFT, ' ', event.getMetaState()); break;
				case KeyEvent.KEYCODE_DPAD_UP: ((vt320)buffer).keyPressed(vt320.KEY_UP, ' ', event.getMetaState()); break;
				case KeyEvent.KEYCODE_DPAD_DOWN: ((vt320)buffer).keyPressed(vt320.KEY_DOWN, ' ', event.getMetaState()); break;
				case KeyEvent.KEYCODE_DPAD_RIGHT: ((vt320)buffer).keyPressed(vt320.KEY_RIGHT, ' ', event.getMetaState()); break;
				default: this.stdin.write(key);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return true;
	}

	public int charWidth = -1,
		charHeight = -1,
		charDescent = -1;
	
	public void setFontSize(float size) {
		this.defaultPaint.setTextSize(size);
		
		// read new metrics to get exact pixel dimensions
		FontMetricsInt fm = this.defaultPaint.getFontMetricsInt();
		this.charDescent = fm.descent;
		
		float[] widths = new float[1];
		this.defaultPaint.getTextWidths("X", widths);
		this.charWidth = (int)widths[0];
		this.charHeight = Math.abs(fm.top) + Math.abs(fm.descent);
		
		// we probably need to resize the viewport with changed size
		// behave just as if the surface changed to reset buffer size
		this.surfaceChanged(this.current, -1, -1, -1);
	}
	
	public boolean newSurface = false;

	public synchronized void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		
		// mark that we need the entire surface repainted
		this.current = holder;
		this.newSurface = true;
		
		if(this.current == null) return;
		
		// resize the underlying buffer as needed
		Rect size = this.current.getSurfaceFrame();
		int termWidth = size.width() / charWidth;
		int termHeight = size.height() / charHeight;
		
		buffer.setScreenSize(termWidth, termHeight, true);
		buffer.height = termHeight;  // TODO: is this really needed?
		
		Log.d(this.getClass().toString(), "surfaceChanged() now width=" + termWidth + ", height=" + termHeight);
		this.redraw();

	}

	public synchronized void surfaceCreated(SurfaceHolder holder) {
		// someone created our Surface, so resize terminal as needed and repaint
		// we handle this just like we would a changing surface
		this.surfaceChanged(holder, -1, -1, -1);
		
	}

	public synchronized void surfaceDestroyed(SurfaceHolder holder) {
		this.current = null;
		this.newSurface = false;
		
	}

	
	public void setVDUBuffer(VDUBuffer buffer) {
		this.buffer = buffer;
	}

	public VDUBuffer getVDUBuffer() {
		return buffer;
	}
	
	private int color[] = { Color.BLACK, Color.RED, Color.GREEN, Color.YELLOW,
		Color.BLUE, Color.MAGENTA, Color.CYAN, Color.WHITE, };
	
	private int darkerColor[] = color;
	
	private final static int COLOR_FG_STD = 7;
	private final static int COLOR_BG_STD = 0;

	public void redraw() {
		// render our buffer only if we have a surface
		if(this.current == null) return;
		
		long time = System.currentTimeMillis();
		int lines = 0;

		int fg, bg;
		boolean entireDirty = buffer.update[0] || newSurface;
		
		// walk through all lines in the buffer
		for(int l = 0; l < buffer.height; l++) {
			
			// check if this line is dirty and needs to be repainted
			// also check for entire-buffer dirty flags
			if(!entireDirty && !buffer.update[l + 1]) continue;
			lines++;
			
			// reset dirty flag for this line
			buffer.update[l + 1] = false;
			
			// lock this entire row as being dirty
			Rect dirty = new Rect(0, l * charHeight, buffer.width * charWidth, (l + 1) * charHeight);
			Canvas canvas = this.current.lockCanvas(dirty);
			
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
			this.current.unlockCanvasAndPost(canvas);
		}
		
		// reset entire-buffer flags
		buffer.update[0] = false;
		this.newSurface = false;
		
		// dump out rendering statistics
		time = System.currentTimeMillis() - time;
		Log.d(this.getClass().toString(), "redraw called and updated lines=" + lines + " taking ms=" + time);
		
	}

	public void updateScrollBar() {
		// TODO Auto-generated method stub
		
	}

	

}
