package org.connectbot;

import org.connectbot.util.HostDatabase;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.support.annotation.ColorRes;
import android.support.annotation.StringRes;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.UiController;
import android.support.test.espresso.ViewAction;
import android.support.test.espresso.action.CloseKeyboardAction;
import android.support.test.espresso.contrib.RecyclerViewActions;
import android.support.test.espresso.intent.Intents;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;

import static android.support.test.espresso.Espresso.onData;
import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static android.support.test.espresso.action.ViewActions.click;
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
import static org.connectbot.ConnectbotMatchers.hasHolderItem;
import static org.connectbot.ConnectbotMatchers.withColoredText;
import static org.connectbot.ConnectbotMatchers.withConnectedHost;
import static org.connectbot.ConnectbotMatchers.withDisconnectedHost;
import static org.connectbot.ConnectbotMatchers.withHostNickname;
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
		Context testContext = InstrumentationRegistry.getTargetContext();
		HostDatabase.resetInMemoryInstance(testContext);

		mActivityRule.launchActivity(new Intent());
	}

	//@Test
	public void localConnectionDisconnectFromHostList() {
		startNewLocalConnection();

		onView(withId(R.id.console_flip)).perform(closeSoftKeyboard(), pressBack());

		// Make sure we're still connected.
		onView(withId(R.id.list))
				.check(hasHolderItem(allOf(withHostNickname("Local"), withConnectedHost())))
				.perform(RecyclerViewActions.actionOnHolderItem(
						allOf(withHostNickname("Local"), withConnectedHost()), longClick()));

		// Click on the disconnect context menu item.
		onView(withText(R.string.list_host_disconnect)).check(matches(isDisplayed())).perform(click());

		// Now make sure we're disconnected.
		onView(withId(R.id.list)).check(hasHolderItem(allOf(withHostNickname("Local"), withDisconnectedHost())));
	}

	//@Test
	public void localConnectionDisconnectConsoleActivity() {
		startNewLocalConnection();

		openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getTargetContext());

		// Click on the disconnect context menu item.
		onView(withText(R.string.list_host_disconnect)).check(matches(isDisplayed())).perform(click());

		// Now make sure we're disconnected.
		onView(withId(R.id.list)).check(hasHolderItem(allOf(withHostNickname("Local"), withDisconnectedHost())));
	}

	//@Test
	public void localConnectionCanDelete() {
		startNewLocalConnectionAndGoBack("Local");
		onView(withId(R.id.list)).perform(RecyclerViewActions.actionOnHolderItem(withHostNickname("Local"), longClick()));
		onView(withText(R.string.list_host_delete)).perform(click());
		onView(withText(R.string.delete_pos)).perform(click());
	}

	//@Test
	public void localConnectionCanChangeToRed() {
		startNewLocalConnectionAndGoBack("RedLocal");
		changeColor("RedLocal", R.color.red, R.string.color_red);
	}

	/**
	 * Changes the color of {@code hostName} from the {@link HostListActivity} to the {@code color}
	 * from {@code R.color.[color]} with identifying {@code stringForColor} from
	 * {@code R.string.[colorname]}.
	 */
	private void changeColor(String hostName, @ColorRes int color, @StringRes int stringForColor) {
		// Bring up the context menu.
		onView(withId(R.id.list)).perform(RecyclerViewActions.actionOnHolderItem(withHostNickname(hostName), longClick()));
		onView(withText(R.string.list_host_edit)).perform(click());

		// Click on the color category and select the desired one.
		onView(withText(R.string.hostpref_color_title)).perform(click());
		onView(withText(stringForColor)).perform(click());

		// Go back to the host list.
		onView(withText(R.string.hostpref_color_title)).perform(pressBack());

		Resources res = InstrumentationRegistry.getTargetContext().getResources();
		onView(withId(R.id.list)).check(hasHolderItem(withColoredText(res.getColor(color))));
	}

	private void startNewLocalConnectionAndGoBack(String name) {
		startNewLocalConnection(name);
		onView(withId(R.id.console_flip)).perform(closeSoftKeyboard(), pressBack());
		onView(withId(R.id.list)).check(hasHolderItem(withHostNickname(name)));
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

	/*
	 * This is to work around a race condition where the software keyboard does not dismiss in time
	 * and you get a Security Exception.
	 *
	 * From: https://code.google.com/p/android-test-kit/issues/detail?id=79#c7
	 */
	public static ViewAction closeSoftKeyboard() {
		return new ViewAction() {
			/**
			 * The delay time to allow the soft keyboard to dismiss.
			 */
			private static final long KEYBOARD_DISMISSAL_DELAY_MILLIS = 1000L;

			/**
			 * The real {@link CloseKeyboardAction} instance.
			 */
			private final ViewAction mCloseSoftKeyboard = new CloseKeyboardAction();

			@Override
			public Matcher<View> getConstraints() {
				return mCloseSoftKeyboard.getConstraints();
			}

			@Override
			public String getDescription() {
				return mCloseSoftKeyboard.getDescription();
			}

			@Override
			public void perform(final UiController uiController, final View view) {
				mCloseSoftKeyboard.perform(uiController, view);
				uiController.loopMainThreadForAtLeast(KEYBOARD_DISMISSAL_DELAY_MILLIS);
			}
		};
	}
}
