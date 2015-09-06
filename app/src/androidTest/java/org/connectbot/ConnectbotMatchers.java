package org.connectbot;

import android.support.annotation.ColorInt;
import android.support.annotation.NonNull;
import android.support.test.espresso.matcher.BoundedMatcher;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import org.connectbot.bean.HostBean;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

import static android.support.test.espresso.matcher.ViewMatchers.hasDescendant;
import static android.support.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.CoreMatchers.allOf;

public class ConnectbotMatchers {
	/**
	 * Matches the nickname of a {@link HostBean}.
	 */
	@NonNull
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
	@NonNull
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
	public static Matcher<View> withTextColor(@ColorInt final int expectedColor) {
		return new TypeSafeMatcher<View>() {
			@Override
			public boolean matchesSafely(View view) {
				if (!(view instanceof TextView)) {
					return false;
				}

				TextView tv = (TextView) view;
				return tv.getCurrentTextColor() == expectedColor;
			}

			@Override
			public void describeTo(Description description) {
				description.appendText("with color '" + Integer.toHexString(expectedColor) + "'");
			}
		};
	}

	@NonNull
	public static Matcher<View> hostDisconnected() {
		return hasDescendant(allOf(withId(android.R.id.icon),
				withDrawableState(android.R.attr.state_expanded)));
	}

	@NonNull
	public static Matcher<View> hostConnected() {
		return hasDescendant(allOf(withId(android.R.id.icon),
				withDrawableState(android.R.attr.state_checked)));
	}
}
