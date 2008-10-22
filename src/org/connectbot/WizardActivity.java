package org.connectbot;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ScrollView;
import android.widget.ViewFlipper;

public class WizardActivity extends Activity {
	
	public final static int ACTION_NEXT = +1, ACTION_PREV = -1;
	
	protected Handler actionHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			
			switch(msg.what) {
			case ACTION_NEXT:
				if(flipper.getDisplayedChild() == flipper.getChildCount() - 1) {
					WizardActivity.this.setResult(Activity.RESULT_OK);
					WizardActivity.this.finish();
				} else {
					flipper.showNext();
				}
				break;
			case ACTION_PREV:
				if(flipper.getDisplayedChild() == 0) {
					WizardActivity.this.setResult(Activity.RESULT_CANCELED);
					WizardActivity.this.finish();
				} else {
					flipper.showPrevious();
				}
				break;
				
			}
			
			// scroll to top and hide all views except current
			scroll.scrollTo(0, 0);
			
			setButtons();
			
		}
	};
	
	protected void setButtons() {
		boolean eula = (flipper.getDisplayedChild() == 0);
		
		next.setText(eula ? "Agree" : "Next");
		prev.setText(eula ? "Cancel" : "Back");
		
	}
	
	protected ScrollView scroll = null;
	protected ViewFlipper flipper = null;
	protected Button next, prev;
	
	protected ImageView finger = null,
		cross = null;
	
	protected Animation animFinger = null,
		animCross = null;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_wizard);
		
		// let the user step through various parts of wizard
		// inflate all views to walk through
		
		LayoutInflater inflater = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		
		this.scroll = (ScrollView)this.findViewById(R.id.wizard_scroll);
		this.flipper = (ViewFlipper)this.findViewById(R.id.wizard_flipper);
		
		this.flipper.addView(inflater.inflate(R.layout.wiz_eula, this.flipper, false));
		this.flipper.addView(inflater.inflate(R.layout.wiz_features, this.flipper, false));
		
		next = (Button)this.findViewById(R.id.action_next);
		next.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				actionHandler.sendEmptyMessage(ACTION_NEXT);
			}
		});
		
		prev = (Button)this.findViewById(R.id.action_prev);
		prev.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				actionHandler.sendEmptyMessage(ACTION_PREV);
			}
		});
		
		this.setButtons();
		
		
	}
	
}
