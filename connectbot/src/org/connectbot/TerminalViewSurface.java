package org.connectbot;

import org.connectbot.service.TerminalBridgeSurface;

import android.content.Context;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup.LayoutParams;

public class TerminalViewSurface extends SurfaceView {
	
	public final Context context;
	public final TerminalBridgeSurface bridge;

	public TerminalViewSurface(Context context, TerminalBridgeSurface bridge) {
		super(context);
		
		this.context = context;
		this.bridge = bridge;
		
		this.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
		this.setFocusable(true);
		this.setFocusableInTouchMode(true);
		
		
		// connect our surface up to the bridge
		this.getHolder().setType(SurfaceHolder.SURFACE_TYPE_HARDWARE);
		this.getHolder().addCallback(bridge);
		this.setOnKeyListener(bridge);
		
	}
	
	// TODO: make sure we pass any keystrokes down to bridge
	

}
