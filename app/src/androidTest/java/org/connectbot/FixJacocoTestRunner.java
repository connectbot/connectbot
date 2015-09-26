/*
 * This class comes from a StackOverflow post:
 * http://stackoverflow.com/questions/30337375/empty-jacoco-report-for-android-espresso/31600193#31600193
 *
 * This should be fixed in com.android.test.support:runner:0.4 and this class can be removed.
 */
package org.connectbot;

import android.os.Bundle;
import android.support.test.runner.AndroidJUnitRunner;
import android.util.Log;

import java.lang.reflect.Method;

public class FixJacocoTestRunner extends AndroidJUnitRunner {

	static {
		System.setProperty("jacoco-agent.destfile", "/data/data/" + BuildConfig.APPLICATION_ID + "/coverage.ec");
	}

	@Override
	public void finish(int resultCode, Bundle results) {
		try {
			Class rt = Class.forName("org.jacoco.agent.rt.RT");
			Method getAgent = rt.getMethod("getAgent");
			Method dump = getAgent.getReturnType().getMethod("dump", boolean.class);
			Object agent = getAgent.invoke(null);
			dump.invoke(agent, false);
		} catch (Throwable e) {
			Log.d("JACOCO", e.getMessage());
		}
		super.finish(resultCode, results);
	}
}
