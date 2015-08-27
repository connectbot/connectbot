package org.connectbot;

import android.os.SystemClock;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class StartupTest {
	@Rule
	public final ActivityTestRule<HostListActivity> mActivityRule = new ActivityTestRule<>(HostListActivity.class);

	@Test
	public void dummy() {
		SystemClock.sleep(TimeUnit.SECONDS.toMillis(2));
	}
}
