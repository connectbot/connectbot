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

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.ViewFlipper;

/**
 * Show a series of wizard-like steps to the user, which might include an EULA,
 * program credits, and helpful hints.
 * 
 * @author jsharkey
 */
public class WizardActivity extends Activity {

	/**
	 * In-order list of wizard steps to present to user.  These are layout resource ids.
	 */
	public final static int[] STEPS = new int[] { R.layout.wiz_eula, R.layout.wiz_features };

	protected ViewFlipper flipper = null;
	protected Button next, prev;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_wizard);
		
		this.flipper = (ViewFlipper)this.findViewById(R.id.wizard_flipper);
		
		// inflate the layouts for each step
		LayoutInflater inflater = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		for(int layout : STEPS) {
			View step = inflater.inflate(layout, this.flipper, false);
			this.flipper.addView(step);
		}
		
		next = (Button)this.findViewById(R.id.action_next);
		next.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if(isLastDisplayed()) {
					// user walked past end of wizard, so return okay
					WizardActivity.this.setResult(Activity.RESULT_OK);
					WizardActivity.this.finish();
				} else {
					// show next step and update buttons
					flipper.showNext();
					updateButtons();
				}
			}
		});
		
		prev = (Button)this.findViewById(R.id.action_prev);
		prev.setOnClickListener(new OnClickListener() {
			public void onClick(View v) {
				if(isFirstDisplayed()) {
					// user walked past beginning of wizard, so return that they cancelled
					WizardActivity.this.setResult(Activity.RESULT_CANCELED);
					WizardActivity.this.finish();
				} else {
					// show previous step and update buttons
					flipper.showPrevious();
					updateButtons();
				}
			}
		});
		
		this.updateButtons();
		
	}
	
	protected boolean isFirstDisplayed() {
		return (flipper.getDisplayedChild() == 0);
	}
	
	protected boolean isLastDisplayed() {
		return (flipper.getDisplayedChild() == flipper.getChildCount() - 1);
	}
	
	protected void updateButtons() {
		boolean eula = (flipper.getDisplayedChild() == 0);
		
		next.setText(eula ? "Agree" : "Next");
		prev.setText(eula ? "Cancel" : "Back");
		
	}
	
}
