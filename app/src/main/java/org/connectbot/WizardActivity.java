/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root, Jeffrey Sharkey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
	private Button next, prev;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.act_wizard);

		this.flipper = (ViewFlipper) findViewById(R.id.wizard_flipper);

		// inflate the layout for EULA step
		LayoutInflater inflater = LayoutInflater.from(this);
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
		prev.setText(eula ? getString(R.string.delete_neg) : getString(R.string.wizard_back));
	}
}
