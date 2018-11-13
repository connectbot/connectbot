/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2017 Kenny Root, Jeffrey Sharkey
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

import org.connectbot.util.PubkeyDatabase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Context;
import android.content.Intent;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import static androidx.test.InstrumentationRegistry.getTargetContext;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.clearText;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.matcher.ViewMatchers.withId;

/**
 * Created by kenny on 2/26/17.
 */
@RunWith(AndroidJUnit4.class)
public class EditHostActivityTest {
	@Rule
	public final ActivityTestRule<EditHostActivity> mActivityRule = new ActivityTestRule<>(
			EditHostActivity.class, false, false);

	@Before
	public void makeDatabasePristine() {
		Context testContext = getTargetContext();
		PubkeyDatabase.resetInMemoryInstance(testContext);

		mActivityRule.launchActivity(new Intent());
	}

	@Test
	public void checkFontSizeEntry() throws Exception {
		onView(withId(R.id.font_size_text)).perform(scrollTo(), clearText());
		onView(withId(R.id.nickname_field)).perform(click());
	}
}