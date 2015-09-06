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
import android.content.res.Resources;
import android.support.annotation.ColorInt;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.support.annotation.StringRes;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.intent.Intents;
import android.support.test.espresso.matcher.BoundedMatcher;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.closeSoftKeyboard;
import static android.support.test.espresso.action.ViewActions.longClick;
import static android.support.test.espresso.action.ViewActions.pressBack;
import static android.support.test.espresso.action.ViewActions.pressImeActionButton;
import static android.support.test.espresso.action.ViewActions.pressMenuKey;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.intent.Intents.intended;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static android.support.test.espresso.matcher.ViewMatchers.hasDescendant;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.connectbot.ConnectbotMatchers.hostConnected;
import static org.connectbot.ConnectbotMatchers.hostDisconnected;
import static org.connectbot.ConnectbotMatchers.withHostNickname;
import static org.connectbot.ConnectbotMatchers.withTextColor;
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
				.check(matches(hostConnected()))
				.perform(longClick());

		// Click on the disconnect context menu item.
		onView(withText(R.string.list_host_disconnect)).check(matches(isDisplayed())).perform(click());

		// Now make sure we're disconnected.
		onData(withHostNickname("Local")).inAdapterView(withId(android.R.id.list))
				.check(matches(hostDisconnected()));
	}

	@Test
	public void localConnectionDisconnectConsoleActivity() {
		startNewLocalConnection();

		onView(withId(R.id.console_flip)).perform(pressMenuKey());

		// Click on the disconnect context menu item.
		onView(withText(R.string.list_host_disconnect)).check(matches(isDisplayed())).perform(click());

		// Now make sure we're disconnected.
		onData(withHostNickname("Local")).inAdapterView(withId(android.R.id.list))
				.check(matches(hostDisconnected()));
	}

	@Test
	public void localConnectionCanChangeToRed() throws Exception {
		startNewLocalConnectionAndGoBack("Local1");
		changeColor("Local1", R.color.red, R.string.color_red);
	}

	/**
	 * Changes the color of {@code hostName} from the {@link HostListActivity} to the {@code color}
	 * from {@code R.color.[color]} with identifying {@code stringForColor} from
	 * {@code R.string.[colorname]}.
	 */
	private void changeColor(String hostName, @ColorRes int color, @StringRes int stringForColor) {
		// Bring up the context menu.
		onData(withHostNickname(hostName)).inAdapterView(withId(android.R.id.list))
				.perform(longClick());
		onView(withText(R.string.list_host_edit)).perform(click());

		// Click on the color category and select the desired one.
		onView(withText(R.string.hostpref_color_title)).perform(click());
		onView(withText(stringForColor)).perform(click());

		// Go back to the host list.
		onView(withText(R.string.hostpref_color_title)).perform(pressBack());

		Resources res = InstrumentationRegistry.getTargetContext().getResources();
		onData(withHostNickname(hostName)).inAdapterView(withId(android.R.id.list))
				.check(matches(hasDescendant(allOf(withId(android.R.id.text1),
						withTextColor(res.getColor(color))))));
	}

	private void startNewLocalConnectionAndGoBack(String name) {
		startNewLocalConnection(name);
		onView(withId(R.id.console_flip)).perform(closeSoftKeyboard(), pressBack());
		onData(withHostNickname(name)).inAdapterView(withId(android.R.id.list))
				.check(matches(isDisplayed()));
	}

	private void startNewLocalConnection() {
		startNewLocalConnection("Local");
	}

	private void startNewLocalConnection(String name) {
		onView(withId(R.id.transport_selection)).perform(click());
		onData(allOf(is(instanceOf(String.class)), is("local"))).perform(click());
		onView(withId(R.id.front_quickconnect)).perform(typeText(name));

		Intents.init();
		try {
			onView(withId(R.id.front_quickconnect)).perform(pressImeActionButton());
			intended(hasComponent(ConsoleActivity.class.getName()));
		} finally {
			Intents.release();
		}

		onView(withId(R.id.console_flip)).check(matches(
				hasDescendant(allOf(isDisplayed(), withId(R.id.terminal_view)))));
	}
}
