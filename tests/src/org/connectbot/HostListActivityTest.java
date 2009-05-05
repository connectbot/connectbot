package org.connectbot;

import android.test.ActivityInstrumentationTestCase;

/**
 * This is a simple framework for a test of an Application.  See
 * {@link android.test.ApplicationTestCase ApplicationTestCase} for more information on
 * how to write and extend Application tests.
 * <p/>
 * To run this test, you can type:
 * adb shell am instrument -w \
 * -e class org.connectbot.HostListActivityTest \
 * org.connectbot.tests/android.test.InstrumentationTestRunner
 */
public class HostListActivityTest extends ActivityInstrumentationTestCase<HostListActivity> {

    public HostListActivityTest() {
        super("org.connectbot", HostListActivity.class);
    }

}
