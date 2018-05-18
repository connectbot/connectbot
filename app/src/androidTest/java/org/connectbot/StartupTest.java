package org.connectbot;

import org.connectbot.util.HostDatabase;
import org.connectbot.util.PreferenceConstants;
import org.hamcrest.Matcher;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.preference.PreferenceManager;
import android.support.annotation.ColorRes;
import android.support.annotation.StringRes;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.UiController;
import android.support.test.espresso.ViewAction;
import android.support.test.espresso.action.CloseKeyboardAction;
import android.support.test.espresso.intent.Intents;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.View;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.action.ViewActions.longClick;
import static android.support.test.espresso.action.ViewActions.pressBack;
import static android.support.test.espresso.action.ViewActions.swipeDown;
import static android.support.test.espresso.action.ViewActions.swipeUp;
import static android.support.test.espresso.action.ViewActions.typeText;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.contrib.RecyclerViewActions.actionOnHolderItem;
import static android.support.test.espresso.intent.Intents.intended;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static android.support.test.espresso.matcher.ViewMatchers.hasDescendant;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.isEnabled;
import static android.support.test.espresso.matcher.ViewMatchers.withContentDescription;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.connectbot.ConnectbotMatchers.hasHolderItem;
import static org.connectbot.ConnectbotMatchers.withColoredText;
import static org.connectbot.ConnectbotMatchers.withConnectedHost;
import static org.connectbot.ConnectbotMatchers.withDisconnectedHost;
import static org.connectbot.ConnectbotMatchers.withHostNickname;
import static org.hamcrest.CoreMatchers.allOf;

@RunWith(AndroidJUnit4.class)
public class StartupTest {
	/**
	 * The delay time to allow the soft keyboard to dismiss.
	 */
	private static final long KEYBOARD_DISMISSAL_DELAY_MILLIS = 1000L;

	@Rule
	public final ActivityTestRule<HostListActivity> mActivityRule = new ActivityTestRule<>(
			HostListActivity.class, false, false);

	@Before
	public void makeDatabasePristine() {
		Context testContext = InstrumentationRegistry.getTargetContext();
		HostDatabase.resetInMemoryInstance(testContext);

		mActivityRule.launchActivity(new Intent());
	}

	@Test
	public void canToggleSoftKeyboardVisibility() {
		// First change preferences so that show/hide keyboard button will not auto-hide
		Context testContext = InstrumentationRegistry.getTargetContext();
		SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(testContext);
		SharedPreferences.Editor editor = settings.edit();
		boolean wasAlwaysVisible = settings.getBoolean(PreferenceConstants.KEY_ALWAYS_VISIVLE, false);
		editor.putBoolean(PreferenceConstants.KEY_ALWAYS_VISIVLE, true).apply();

		startNewLocalConnection();
		hideAndShowSoftKeyboard();

		editor.putBoolean(PreferenceConstants.KEY_ALWAYS_VISIVLE, wasAlwaysVisible).commit();
	}

	@Test
	public void localConnectionDisconnectFromHostList() {
		startNewLocalConnection();

		onView(withId(R.id.console_flip)).perform(closeSoftKeyboard(), pressBack());

		// Make sure we're still connected.
		onView(withId(R.id.list))
				.check(hasHolderItem(allOf(withHostNickname("Local"), withConnectedHost())))
				.perform(actionOnHolderItem(
						allOf(withHostNickname("Local"), withConnectedHost()), longClick()));

		// Click on the disconnect context menu item.
		onView(withText(R.string.list_host_disconnect)).check(matches(isDisplayed())).perform(click());

		// Now make sure we're disconnected.
		onView(withId(R.id.list)).check(hasHolderItem(allOf(withHostNickname("Local"), withDisconnectedHost())));
	}

	@Test
	public void localConnectionDisconnectConsoleActivity() {
		startNewLocalConnection();

		openActionBarOverflowOrOptionsMenu(InstrumentationRegistry.getTargetContext());

		// Click on the disconnect context menu item.
		onView(withText(R.string.list_host_disconnect)).check(matches(isDisplayed())).perform(click());

		// Now make sure we're disconnected.
		onView(withId(R.id.list)).check(hasHolderItem(allOf(withHostNickname("Local"), withDisconnectedHost())));
	}

	@Test
	public void localConnectionCanDelete() {
		startNewLocalConnectionAndGoBack("Local");
		onView(withId(R.id.list)).perform(actionOnHolderItem(withHostNickname("Local"), longClick()));
		onView(withText(R.string.list_host_delete)).perform(click());
		onView(withText(R.string.delete_pos)).perform(click());
	}

	@Test
	public void localConnectionCanChangeToRed() {
		startNewLocalConnectionAndGoBack("RedLocal");
		changeColor("RedLocal", R.color.red, R.string.color_red);
	}

	@Test
	public void canScrollTerminal() {
		startNewLocalConnection();
		onView(withId(R.id.terminal_view))
				.perform(closeSoftKeyboard(), longClick(), swipeUp(), swipeDown());
	}

	@Test
	public void addHostThenCancelAndDiscard() {
		onView(withId(R.id.add_host_button)).perform(click());
		onView(withId(R.id.quickconnect_field)).perform(typeText("abandoned"), closeSoftKeyboard(),
				pressBack());
		onView(withText(R.string.discard_host_changes_message)).check(matches(isDisplayed()));
		onView(withText(R.string.discard_host_button)).perform(click());
		onView(withId(R.id.add_host_button)).check(matches(isDisplayed()));
	}

	@Test
	public void addHostThenCancelAndKeepEditing() {
		onView(withId(R.id.add_host_button)).perform(click());
		onView(withId(R.id.quickconnect_field)).perform(typeText("abandoned"), closeSoftKeyboard(),
				pressBack());
		onView(withText(R.string.discard_host_changes_message)).check(matches(isDisplayed()));
		onView(withText(R.string.discard_host_cancel_button)).perform(click());
		onView(withId(R.id.quickconnect_field)).check(matches(isDisplayed()));
	}

	/**
	 * Changes the color of {@code hostName} from the {@link HostListActivity} to the {@code color}
	 * from {@code R.color.[color]} with identifying {@code stringForColor} from
	 * {@code R.string.[colorname]}.
	 */
	private void changeColor(String hostName, @ColorRes int color, @StringRes int stringForColor) {
		// Bring up the context menu.
		onView(withId(R.id.list)).perform(actionOnHolderItem(withHostNickname(hostName), longClick()));
		onView(withText(R.string.list_host_edit)).perform(click());

		// Click on the color category and select the desired one.
		onView(withText(R.string.hostpref_color_title)).perform(click());
		onView(withText(stringForColor)).perform(click());

		// Go back to the host list.
		onView(withId(R.id.save)).perform(click());

		Resources res = InstrumentationRegistry.getTargetContext().getResources();
		onView(withId(R.id.list)).check(hasHolderItem(withColoredText(res.getColor(color))));
	}

	private void hideAndShowSoftKeyboard() {
		onView(withId(R.id.console_flip)).perform(closeSoftKeyboard());
		onView(withContentDescription(R.string.image_description_show_keyboard)).perform(click());
		onView(withId(R.id.console_flip)).perform(loopMainThreadFor(KEYBOARD_DISMISSAL_DELAY_MILLIS));
		onView(withContentDescription(R.string.image_description_hide_keyboard)).perform(click());
		onView(withId(R.id.console_flip)).perform(loopMainThreadFor(KEYBOARD_DISMISSAL_DELAY_MILLIS));
		onView(withContentDescription(R.string.image_description_show_keyboard)).perform(click());
		onView(withId(R.id.console_flip)).perform(pressBack());
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
		onView(withId(R.id.add_host_button)).perform(click());
		onView(withId(R.id.protocol_text)).perform(click());
		onView(withText("local")).perform(click());
		onView(withId(R.id.quickconnect_field)).perform(typeText(name));
		onView(withId(R.id.save)).perform(click());

		Intents.init();
		try {
			onView(withId(R.id.list)).perform(actionOnHolderItem(
					withHostNickname(name), click()));
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

	public static ViewAction loopMainThreadFor(final long millis) {
		return new ViewAction() {

			@Override
			public Matcher<View> getConstraints() {
				return isEnabled();
			}

			@Override
			public String getDescription() {
				return "Rturns an action that loops the main thread for at least " + millis +"ms.";
			}

			@Override
			public void perform(final UiController uiController, final View view) {
				uiController.loopMainThreadForAtLeast(millis);
			}
		};
	}
}
