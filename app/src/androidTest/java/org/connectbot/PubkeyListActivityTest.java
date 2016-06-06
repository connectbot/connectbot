/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2016 Kenny Root, Jeffrey Sharkey
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

import org.connectbot.util.EntropyView;
import org.connectbot.util.HostDatabase;
import org.connectbot.util.PubkeyDatabase;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Context;
import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.UiController;
import android.support.test.espresso.ViewAction;
import android.support.test.espresso.action.CloseKeyboardAction;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;
import android.widget.EditText;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.scrollTo;
import static android.support.test.espresso.action.ViewActions.swipeDown;
import static android.support.test.espresso.action.ViewActions.swipeLeft;
import static android.support.test.espresso.action.ViewActions.swipeRight;
import static android.support.test.espresso.action.ViewActions.swipeUp;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static org.connectbot.ConnectbotMatchers.hasHolderItem;
import static org.connectbot.ConnectbotMatchers.withHostNickname;
import static org.connectbot.ConnectbotMatchers.withPubkeyNickname;
import static org.hamcrest.Matchers.allOf;
import static org.junit.Assert.*;

/**
 * Created by kenny on 6/6/16.
 */
@RunWith(AndroidJUnit4.class)
public class PubkeyListActivityTest {
	@Rule
	public final ActivityTestRule<PubkeyListActivity> mActivityRule = new ActivityTestRule<>(
			PubkeyListActivity.class, false, false);

	@Before
	public void makeDatabasePristine() {
		Context testContext = InstrumentationRegistry.getTargetContext();
		PubkeyDatabase.resetInMemoryInstance(testContext);

		mActivityRule.launchActivity(new Intent());
	}

	@Test
	public void generateRSAKey() {
		onView(withId(R.id.add_new_key_icon)).perform(click());
		onView(withId(R.id.nickname)).perform(typeText("test1"));
		onView(withId(R.id.save)).perform(scrollTo(), click());
		onView(withId(R.id.entropy)).perform(fillEntropy());
		onView(withId(R.id.list)).check(hasHolderItem(withPubkeyNickname("test1")));
	}

	private ViewAction fillEntropy() {
		return new ViewAction() {
			@Override
			public Matcher<View> getConstraints() {
				return allOf(isDisplayed(), isAssignableFrom(EntropyView.class));
			}

			@Override
			public String getDescription() {
				return "Dismisses the 'Gathering entropy...' dialog";
			}

			@Override
			public void perform(final UiController uiController, final View view) {
				((EntropyView) view).notifyListeners();
			}
		};
	}
}