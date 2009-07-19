package com.nullwire.trace;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.Random;

import android.util.Log;

public class DefaultExceptionHandler implements UncaughtExceptionHandler {

	private static final String TAG = "UNHANDLED_EXCEPTION";

	// Default exception handler
	public void uncaughtException(Thread t, Throwable e) {
		// Here you should have a more robust, permanent record of problems
		final Writer result = new StringWriter();
		final PrintWriter printWriter = new PrintWriter(result);
		e.printStackTrace(printWriter);
		try {
			// Random number to avoid duplicate files
			Random generator = new Random();
			int random = generator.nextInt(99999);
			// Embed version in stacktrace filename
			String filename = G.APP_VERSION + "-" + Integer.toString(random);
			Log.d(TAG, "Writing unhandled exception to: " + G.FILES_PATH + "/"
					+ filename + ".stacktrace");
			// Write the stacktrace to disk
			BufferedWriter bos = new BufferedWriter(new FileWriter(G.FILES_PATH
					+ "/" + filename + ".stacktrace"));
			bos.write(G.APP_VERSION);
			bos.write(G.APP_DESCRIPTION);
			bos.write('\n');
			bos.write(result.toString());
			bos.flush();
			// Close up everything
			bos.close();
		} catch (Exception ebos) {
			// Nothing much we can do about this - the game is over
			ebos.printStackTrace();
		}
		Log.d(TAG, result.toString());
		// FlurryAgent session has ended
		t.getThreadGroup().destroy();
	}
}
