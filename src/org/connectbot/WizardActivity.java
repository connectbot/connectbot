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

import org.connectbot.util.HelpTopicView;

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
	protected ViewFlipper flipper = null;
	protected Button next, prev;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_wizard);
		
		this.flipper = (ViewFlipper) findViewById(R.id.wizard_flipper);
		
		// inflate the layout for EULA step
		LayoutInflater inflater = (LayoutInflater)this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
		this.flipper.addView(inflater.inflate(R.layout.wiz_eula, this.flipper, false));

		// Add a view for each help topic we want the user to see.
		String[] topics = getResources().getStringArray(R.array.list_wizard_topics);
		for (String topic : topics) {
			flipper.addView(new HelpTopicView(this).setTopic(topic));
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
		
		next.setText(eula ? getString(R.string.wizard_agree) : getString(R.string.wizard_next));
		prev.setText(eula ? getString(R.string.wizard_cancel) : getString(R.string.wizard_back));
	}
}
