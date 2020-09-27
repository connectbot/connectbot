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

import java.security.KeyPair;
import java.util.Collection;
import java.util.concurrent.TimeUnit;

import org.connectbot.util.EntropyView;
import org.connectbot.util.OnKeyGeneratedListener;
import org.connectbot.util.PubkeyDatabase;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import androidx.test.espresso.IdlingPolicy;
import androidx.test.espresso.IdlingResource;
import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry;
import android.view.View;

import static androidx.test.InstrumentationRegistry.getInstrumentation;
import static androidx.test.InstrumentationRegistry.getTargetContext;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.registerIdlingResources;
import static androidx.test.espresso.IdlingPolicies.getDynamicIdlingResourceErrorPolicy;
import static androidx.test.espresso.IdlingPolicies.getMasterIdlingPolicy;
import static androidx.test.espresso.IdlingPolicies.setIdlingResourceTimeout;
import static androidx.test.espresso.IdlingPolicies.setMasterPolicyTimeout;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.runner.lifecycle.Stage.RESUMED;
import static org.connectbot.ConnectbotMatchers.hasHolderItem;
import static org.connectbot.ConnectbotMatchers.withPubkeyNickname;
import static org.hamcrest.Matchers.allOf;

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
		Context testContext = getTargetContext();
		PubkeyDatabase.resetInMemoryInstance(testContext);

		mActivityRule.launchActivity(new Intent());
	}

	@Test
	public void generateRSAKey() {
		onView(withId(R.id.add_new_key_icon)).perform(click());

		KeyGenerationIdlingResource keyGenerationIdlingResource = new KeyGenerationIdlingResource(
				((GeneratePubkeyActivity) getDisplayedActivityInstance()));

		onView(withId(R.id.nickname)).perform(typeText("test1"));
		onView(withId(R.id.save)).perform(ScrollToAction.betterScrollTo(), click());
		onView(withId(R.id.entropy)).perform(fillEntropy());

		IdlingPolicy masterPolicy = getMasterIdlingPolicy();
		IdlingPolicy resourcePolicy = getDynamicIdlingResourceErrorPolicy();

		try {
			setMasterPolicyTimeout(10 * 60, TimeUnit.SECONDS);
			setIdlingResourceTimeout(10 * 60, TimeUnit.SECONDS);

			registerIdlingResources(keyGenerationIdlingResource);

			Thread.sleep(5000);
			onView(withId(R.id.list)).check(hasHolderItem(withPubkeyNickname("test1")));
		} catch (InterruptedException e) {
			System.err.println("*** Error while scrolling up:");
			System.err.println("--- BEGIN STACK TRACE ---");
			e.printStackTrace();
			System.err.println("--- END STACK TRACE ---");
		} finally {
			setMasterPolicyTimeout(masterPolicy.getIdleTimeout(), masterPolicy.getIdleTimeoutUnit());
			setIdlingResourceTimeout(resourcePolicy.getIdleTimeout(), resourcePolicy.getIdleTimeoutUnit());
		}
	}

	public Activity getDisplayedActivityInstance() {
		final Activity[] currentActivity = new Activity[1];
		getInstrumentation().runOnMainSync(new Runnable() {
			@Override
			public void run() {
				Collection<Activity> resumedActivities = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(RESUMED);
				if (resumedActivities.iterator().hasNext()) {
					currentActivity[0] = resumedActivities.iterator().next();
				}
			}
		});

		return currentActivity[0];
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

	private static class KeyGenerationIdlingResource implements IdlingResource,
			OnKeyGeneratedListener {
		private boolean isIdle;
		private ResourceCallback callback;

		public KeyGenerationIdlingResource(GeneratePubkeyActivity activity) {
			isIdle = false;
			activity.setListener(this);
		}

		@Override
		public String getName() {
			return "Key Generator Idling Resource";
		}

		@Override
		public boolean isIdleNow() {
			return isIdle;
		}

		@Override
		public void registerIdleTransitionCallback(ResourceCallback callback) {
			this.callback = callback;
		}

		@Override
		public void onGenerationError(Exception e) {
			isIdle = true;
			if (callback != null) {
				callback.onTransitionToIdle();
			}
		}

		@Override
		public void onGenerationSuccess(KeyPair keyPair) {
			isIdle = true;
			if (callback != null) {
				callback.onTransitionToIdle();
			}
		}
	}
}
