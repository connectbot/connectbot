package com.nullwire.trace;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.connectbot.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class ExceptionHandler {
	public static String TAG = "com.nullwire.trace.ExceptionsHandler";

	private static String[] stackTraceFileList = null;

	/**
	 * @param context
	 */
	public static void checkForTraces(final Context context) {
		new Thread(new Runnable() {
			public void run() {
				String[] stackTraces = searchForStackTraces(context);
				if (stackTraces != null && stackTraces.length > 0) {
					Log.d(TAG, "number of stack traces: " + stackTraces.length);
					submissionHandler.sendMessage(
							submissionHandler.obtainMessage(-1, context));
				}
			}
		}).start();
	}

	/**
	 * @param context
	 */
	private static void getPackageInfo(Context context) {
		// Get information about the Package
		PackageManager pm = context.getPackageManager();
		try {
			PackageInfo pi;
			// Version
			pi = pm.getPackageInfo(context.getPackageName(), 0);
			// Package name
			G.APP_PACKAGE = pi.packageName;
			// Version information
			G.APP_VERSION = pi.versionName;
			G.APP_DESCRIPTION = context.getString(R.string.msg_version);
			// Files dir for storing the stack traces
			G.FILES_PATH = context.getFilesDir().getAbsolutePath();
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}

		Log.d(TAG, "APP_VERSION: " + G.APP_VERSION);
		Log.d(TAG, "APP_PACKAGE: " + G.APP_PACKAGE);
		Log.d(TAG, "FILES_PATH: " + G.FILES_PATH);
		Log.d(TAG, "URL: " + G.URL);
	}

	public static void register(Context context) {
		Log.i(TAG, "Registering default exceptions handler");

		new Thread() {
			@Override
			public void run() {
				// Register default exceptions handler
				Thread.setDefaultUncaughtExceptionHandler(new DefaultExceptionHandler());
			}
		}.start();
	}

	/**
	 * Search for stack trace files.
	 *
	 * @return
	 */
	private static String[] searchForStackTraces(Context context) {
		if (stackTraceFileList != null && stackTraceFileList.length > 0)
			return stackTraceFileList;

		if (context == null)
			return null;

		if (G.FILES_PATH == null)
			getPackageInfo(context);

		File dir = new File(G.FILES_PATH + "/");
		// Try to create the files folder if it doesn't exist
		dir.mkdir();
		// Filter for ".stacktrace" files
		FilenameFilter filter = new FilenameFilter() {
			public boolean accept(File dir, String name) {
				return name.endsWith(".stacktrace");
			}
		};
		return (stackTraceFileList = dir.list(filter));
	}

	private static Handler submissionHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Context context = (Context) msg.obj;
			ExceptionClickListener clickListener = new ExceptionClickListener(context);
			new AlertDialog.Builder(context)
					.setMessage(R.string.exceptions_submit_message)
					.setPositiveButton(android.R.string.yes, clickListener)
					.setNegativeButton(android.R.string.no, clickListener)
					.create().show();
		}
	};

	/**
	 * Look into the files folder to see if there are any "*.stacktrace" files.
	 * If any are present, submit them to the trace server.
	 */
	public static void submitStackTraces(Context context) {
		if (context == null)
			return;

		try {
			Log.d(TAG, "Looking for exceptions in: " + G.FILES_PATH);
			String[] list = searchForStackTraces(context);
			if (list != null && list.length > 0) {
				Log.d(TAG, "Found " + list.length + " stacktrace(s)");
				StringBuilder contents = new StringBuilder();
				for (int i = 0; i < list.length; i++) {
					String filePath = G.FILES_PATH + "/" + list[i];
					// Extract the version from the filename:
					// "packagename-version-...."
					String version = list[i].split("-")[0];
					Log.d(TAG, "Stacktrace in file '" + filePath
							+ "' belongs to version " + version);
					// Read contents of stacktrace
					contents.setLength(0);
					BufferedReader input;
					try {
						input = new BufferedReader(new FileReader(filePath));
					} catch (FileNotFoundException fnf) {
						continue;
					}
					String line = null;
					while ((line = input.readLine()) != null) {
						contents.append(line);
						contents.append(System.getProperty("line.separator"));
					}
					input.close();
					String stacktrace;
					stacktrace = contents.toString();
					Log.d(TAG, "Transmitting stack trace: " + stacktrace);
					// Transmit stack trace with POST request
					DefaultHttpClient httpClient = new DefaultHttpClient();
					HttpPost httpPost = new HttpPost(G.URL);
					List<NameValuePair> nvps = new ArrayList<NameValuePair>();
					nvps.add(new BasicNameValuePair("package_name", G.APP_PACKAGE));
					nvps.add(new BasicNameValuePair("package_version", version));
					nvps.add(new BasicNameValuePair("stacktrace", stacktrace));
					httpPost.setEntity(new UrlEncodedFormEntity(nvps, HTTP.UTF_8));
					// We don't care about the response, so we just hope it went
					// well and on with it
					httpClient.execute(httpPost);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			removeStackTraces(context);
		}
	}

	public synchronized static void removeStackTraces(Context context) {
		if (context == null)
			return;

		try {
			String[] list = searchForStackTraces(context);

			if (list == null)
				return;

			for (int i = 0; i < list.length; i++) {
				File file = new File(G.FILES_PATH + "/" + list[i]);
				file.delete();
			}

			stackTraceFileList = null;
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
