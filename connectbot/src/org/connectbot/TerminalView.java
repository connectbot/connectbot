package org.connectbot;

import org.connectbot.service.TerminalBridge;
import org.connectbot.service.TerminalBridgeSurface;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.ViewGroup.LayoutParams;

public class TerminalView extends View {

	public final Context context;
	public final TerminalBridge bridge;
	public final Paint paint;

	public TerminalView(Context context, TerminalBridge bridge) {
		super(context);
		
		this.context = context;
		this.bridge = bridge;
		this.paint = new Paint();
		
		this.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		this.setFocusable(true);
		this.setFocusableInTouchMode(true);
		
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
		// draw the bridge if it exists
		if(this.bridge.bitmap != null) {
			canvas.drawBitmap(this.bridge.bitmap, 0, 0, this.paint);
		}
		
	}
	
}
