package org.theb.ssh;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.Paint.FontMetrics;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

public class TouchEntropy extends Activity {
	protected String  mEntropy;
	
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        mEntropy = new String();
        setContentView(new TouchView(this));
    }
    
    class TouchView extends View {
    	Paint mPaint;
    	FontMetrics mFontMetrics;
    	
    	private boolean mFlipFlop = false;
    	private long mLastTime = 0;
    	    	
        public TouchView(Context c) {
            super(c);
        	
        	mPaint = new Paint();
        	mPaint.setAntiAlias(true);
        	mPaint.setTypeface(Typeface.DEFAULT);
        	mPaint.setTextAlign(Paint.Align.CENTER);
        	mPaint.setTextSize(16);
        	mPaint.setColor(Color.WHITE);
        	mFontMetrics = mPaint.getFontMetrics();
        }
        
        @Override
        public void onDraw(Canvas c) {
        	String prompt = getResources().getString(R.string.prompt_touch);
        	c.drawText(prompt + " " + (int)(100.0 * (mEntropy.length() / 20.0)) + "% done",
        			getWidth() / 2,
        			getHeight() / 2 - (mFontMetrics.ascent + mFontMetrics.descent) / 2,
        			mPaint);
        }
        
        @Override
        public boolean onTouchEvent(MotionEvent event) {
        	// Only get entropy every 200 milliseconds to ensure the user has moved around.
        	long now = System.currentTimeMillis();
        	if ((now - mLastTime) < 200)
        		return true;
        	else
        		mLastTime = now;
        	
        	Log.d("SSH", "Last time was " + mLastTime);


        	// Get the lowest 4 bits of each X, Y input and concat to the entropy-gathering
        	// string.
        	if (mFlipFlop)
        		mEntropy += (byte)(((int)event.getX() & 0xF0) | ((int)event.getY() & 0x0F));
        	else
        		mEntropy += (byte)(((int)event.getY() & 0xF0) | ((int)event.getX() & 0x0F));
        	
        	mFlipFlop = !mFlipFlop;
                	
        	// SHA1PRNG only keeps 20 bytes (160 bits) of entropy.
        	if (mEntropy.length() > 20) {
        		Intent intent = TouchEntropy.this.getIntent();
        		intent.putExtra(Intent.EXTRA_TEXT, mEntropy);
        		TouchEntropy.this.setResult(RESULT_OK, intent);
        		TouchEntropy.this.finish();
        	}
        	
        	invalidate();
        	
            return true;
        }
    }
}
