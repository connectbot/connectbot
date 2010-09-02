/**
 *
 */
package com.nullwire.trace;

import java.lang.ref.WeakReference;

import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.util.Log;

/**
 * @author Kenny Root
 *
 */
public class ExceptionClickListener implements OnClickListener {
	public static String TAG = "com.nullwire.trace.ExceptionClickListener";

	WeakReference<Context> context;

	public ExceptionClickListener() { }

	public void onClick(DialogInterface dialog, int whichButton) {
		switch (whichButton) {
		case DialogInterface.BUTTON_POSITIVE:
			dialog.dismiss();
			Log.d(TAG, "Trying to submit stack traces");
			new Thread(new Runnable() {
				public void run() {
					ExceptionHandler.submitStackTraces();
				}
			}).start();
			break;
		case DialogInterface.BUTTON_NEGATIVE:
			dialog.dismiss();
			Log.d(TAG, "Deleting old stack traces.");
			new Thread(new Runnable() {
				public void run() {
					ExceptionHandler.removeStackTraces();
				}
			}).start();
			break;
		default:
			Log.d("ExceptionClickListener", "Got unknown button click: " + whichButton);
			dialog.cancel();
		}
	}
}
