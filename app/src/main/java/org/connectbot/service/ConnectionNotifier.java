/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2010 Kenny Root, Jeffrey Sharkey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot.service;

import org.connectbot.ConsoleActivity;
import org.connectbot.HostListActivity;
import org.connectbot.R;
import org.connectbot.bean.HostBean;
import org.connectbot.util.HostDatabase;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import androidx.core.app.NotificationCompat;

/**
 * @author Kenny Root
 * <p>
 * Based on the concept from jasta's blog post.
 */
public class ConnectionNotifier {
	private static final int ONLINE_NOTIFICATION = 1;
	private static final int ACTIVITY_NOTIFICATION = 2;
	private static final int ONLINE_DISCONNECT_NOTIFICATION = 3;
	int pendingIntentFlags;

	private static final String NOTIFICATION_CHANNEL = "my_connectbot_channel";

	private static class Holder {
		private static final ConnectionNotifier sInstance = new ConnectionNotifier();
	}

	private ConnectionNotifier() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			pendingIntentFlags = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
		} else {
			pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
		}
	}

	public static ConnectionNotifier getInstance() {
		return Holder.sInstance;
	}

	private NotificationManager getNotificationManager(Context context) {
		return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	private NotificationCompat.Builder newNotificationBuilder(Context context, String id) {
		NotificationCompat.Builder builder =
				new NotificationCompat.Builder(context, id)
						.setSmallIcon(R.drawable.notification_icon)
						.setWhen(System.currentTimeMillis());

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			createNotificationChannel(context, id);
		}

		return builder;
	}

	@TargetApi(Build.VERSION_CODES.O)
	private void createNotificationChannel(Context context, String id) {
		NotificationChannel nc = new NotificationChannel(id, context.getString(R.string.app_name),
				NotificationManager.IMPORTANCE_DEFAULT);
		getNotificationManager(context).createNotificationChannel(nc);
	}

	private Notification newActivityNotification(Context context, HostBean host) {
		NotificationCompat.Builder builder = newNotificationBuilder(context, NOTIFICATION_CHANNEL);

		Resources res = context.getResources();

		String contentText = res.getString(
				R.string.notification_text, host.getNickname());

		Intent notificationIntent = new Intent(context, ConsoleActivity.class);
		notificationIntent.setAction("android.intent.action.VIEW");
		notificationIntent.setData(host.getUri());

		PendingIntent contentIntent = PendingIntent.getActivity(context, 0, notificationIntent, pendingIntentFlags);

		builder.setContentTitle(res.getString(R.string.app_name))
				.setContentText(contentText)
				.setContentIntent(contentIntent);

		builder.setAutoCancel(true);

		int ledOnMS = 300;
		int ledOffMS = 1000;
		builder.setDefaults(Notification.DEFAULT_LIGHTS);
		if (HostDatabase.COLOR_RED.equals(host.getColor()))
			builder.setLights(Color.RED, ledOnMS, ledOffMS);
		else if (HostDatabase.COLOR_GREEN.equals(host.getColor()))
			builder.setLights(Color.GREEN, ledOnMS, ledOffMS);
		else if (HostDatabase.COLOR_BLUE.equals(host.getColor()))
			builder.setLights(Color.BLUE, ledOnMS, ledOffMS);
		else
			builder.setLights(Color.WHITE, ledOnMS, ledOffMS);

		return builder.build();
	}

	private Notification newRunningNotification(Context context) {
		NotificationCompat.Builder builder = newNotificationBuilder(context, NOTIFICATION_CHANNEL);

		builder.setOngoing(true);
		builder.setWhen(0);

		builder.setContentIntent(PendingIntent.getActivity(context, ONLINE_NOTIFICATION, new Intent(context, ConsoleActivity.class), pendingIntentFlags));

		Resources res = context.getResources();
		builder.setContentTitle(res.getString(R.string.app_name));
		builder.setContentText(res.getString(R.string.app_is_running));

		Intent disconnectIntent = new Intent(context, HostListActivity.class);
		disconnectIntent.setAction(HostListActivity.DISCONNECT_ACTION);
		builder.addAction(
				android.R.drawable.ic_menu_close_clear_cancel,
				res.getString(R.string.list_host_disconnect),
				PendingIntent.getActivity(context, ONLINE_DISCONNECT_NOTIFICATION, disconnectIntent, pendingIntentFlags));

		return builder.build();
	}

	void showActivityNotification(Service context, HostBean host) {
		getNotificationManager(context).notify(ACTIVITY_NOTIFICATION, newActivityNotification(context, host));
	}

	void showRunningNotification(Service context) {
		context.startForeground(ONLINE_NOTIFICATION, newRunningNotification(context));
	}

	void hideRunningNotification(Service context) {
		context.stopForeground(true);
	}
}
