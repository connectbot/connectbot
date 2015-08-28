package org.connectbot;

import org.connectbot.bean.HostBean;
import org.connectbot.util.HostDatabase;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.ViewAssertion;
import android.support.test.espresso.intent.Intents;
import android.support.test.espresso.matcher.BoundedMatcher;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.closeSoftKeyboard;
import static android.support.test.espresso.action.ViewActions.longClick;
import static android.support.test.espresso.action.ViewActions.pressBack;
import static android.support.test.espresso.action.ViewActions.pressKey;
import static android.support.test.espresso.action.ViewActions.pressMenuKey;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.intent.Intents.intended;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static android.support.test.espresso.matcher.ViewMatchers.hasDescendant;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;

@RunWith(AndroidJUnit4.class)
public class StartupTest {
	@Rule
	public final ActivityTestRule<HostListActivity> mActivityRule = new ActivityTestRule<>(
			HostListActivity.class, false, false);

	@Before
	public void makeDatabasePristine() {
		HostDatabase db = new HostDatabase(InstrumentationRegistry.getTargetContext());
		db.resetDatabase();

		mActivityRule.launchActivity(new Intent());
	}

	@Test
	public void localConnectionDisconnectFromHostList() {
		startNewLocalConnection();

		onView(withId(R.id.console_flip)).perform(closeSoftKeyboard(), pressBack());

		// Make sure we're still connected.
		onData(withHostNickname("Local")).inAdapterView(withId(android.R.id.list))
				.check(hostConnected())
				.perform(longClick());

		// Click on the disconnect context menu item.
		onView(withText(R.string.list_host_disconnect)).check(matches(isDisplayed())).perform(click());

		// Now make sure we're disconnected.
		onData(withHostNickname("Local")).inAdapterView(withId(android.R.id.list))
				.check(hostDisconnected());
	}

	@Test
	public void localConnectionDisconnectConsoleActivity() {
		startNewLocalConnection();

		onView(withId(R.id.console_flip)).perform(pressMenuKey());

		// Click on the disconnect context menu item.
		onView(withText(R.string.list_host_disconnect)).check(matches(isDisplayed())).perform(click());

		// Now make sure we're disconnected.
		onData(withHostNickname("Local")).inAdapterView(withId(android.R.id.list))
				.check(hostDisconnected());
	}

	private void startNewLocalConnection() {
		onView(withId(R.id.transport_selection)).perform(click());
		onData(allOf(is(instanceOf(String.class)), is("local"))).perform(click());
		onView(withId(R.id.front_quickconnect)).perform(typeText("Local"));

		Intents.init();
		onView(withId(R.id.front_quickconnect)).perform(pressKey(KeyEvent.KEYCODE_ENTER));
		intended(hasComponent(ConsoleActivity.class.getName()));
		Intents.release();

		onView(withId(R.id.console_flip)).check(matches(
				hasDescendant(allOf(isDisplayed(), withId(R.id.terminal_view)))));
	}

	/**
	 * Matches the nickname of a {@link HostBean}.
	 */
	public static Matcher<Object> withHostNickname(final String content) {
		return new BoundedMatcher<Object, HostBean>(HostBean.class) {
			@Override
			public boolean matchesSafely(HostBean host) {
				return host.getNickname().matches(content);
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("with host nickname '" + content + "'");
			}
		};
	}

	/**
	 * Matches the drawable state on an ImageView that is set with setImageState.
	 */
	public static Matcher<View> withDrawableState(final int expectedState) {
		return new TypeSafeMatcher<View>() {
			@Override
			public boolean matchesSafely(View view) {
				if (!(view instanceof ImageView)) {
					return false;
				}

				int[] states = view.getDrawableState();
				for (int state : states) {
					if (state == expectedState) {
						return true;
					}
				}
				return false;
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("with drawable state '" + expectedState + "'");
			}
		};
	}

	@NonNull
	private ViewAssertion hostDisconnected() {
		return matches(hasDescendant(allOf(withId(android.R.id.icon),
				withDrawableState(android.R.attr.state_expanded))));
	}

	@NonNull
	private ViewAssertion hostConnected() {
		return matches(hasDescendant(allOf(withId(android.R.id.icon),
				withDrawableState(android.R.attr.state_checked))));
	}
}
