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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.connectbot.ConsoleActivity;
import org.connectbot.HostListActivity;
import org.connectbot.R;
import org.connectbot.bean.HostBean;
import org.connectbot.util.HostDatabase;
import org.connectbot.util.PreferenceConstants;

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
 *
 * Based on the concept from jasta's blog post.
 */
public abstract class ConnectionNotifier {
	private static final int ONLINE_NOTIFICATION = 1;
	private static final int ACTIVITY_NOTIFICATION = 2;
	private static final int ONLINE_DISCONNECT_NOTIFICATION = 3;

	private String id = "my_connectbot_channel";
	NotificationChannel nc;

	public static ConnectionNotifier getInstance() {
		if (PreferenceConstants.PRE_ECLAIR)
			return PreEclair.Holder.sInstance;
		else
			return EclairAndBeyond.Holder.sInstance;
	}

	protected NotificationManager getNotificationManager(Context context) {
		return (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	protected NotificationCompat.Builder newNotificationBuilder(Context context, String id) {
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
		nc = new NotificationChannel(id, context.getString(R.string.app_name),
				NotificationManager.IMPORTANCE_DEFAULT);
		getNotificationManager(context).createNotificationChannel(nc);
	}

	protected Notification newActivityNotification(Context context, HostBean host) {
		NotificationCompat.Builder builder = newNotificationBuilder(context, id);

		Resources res = context.getResources();

		String contentText = res.getString(
				R.string.notification_text, host.getNickname());

		Intent notificationIntent = new Intent(context, ConsoleActivity.class);
		notificationIntent.setAction("android.intent.action.VIEW");
		notificationIntent.setData(host.getUri());

		PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
				notificationIntent, 0);

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

	protected Notification newRunningNotification(Context context) {
		NotificationCompat.Builder builder = newNotificationBuilder(context, id);

		builder.setOngoing(true);
		builder.setWhen(0);

		builder.setContentIntent(PendingIntent.getActivity(context,
				ONLINE_NOTIFICATION,
				new Intent(context, ConsoleActivity.class), 0));

		Resources res = context.getResources();
		builder.setContentTitle(res.getString(R.string.app_name));
		builder.setContentText(res.getString(R.string.app_is_running));

		Intent disconnectIntent = new Intent(context, HostListActivity.class);
		disconnectIntent.setAction(HostListActivity.DISCONNECT_ACTION);
		builder.addAction(
				android.R.drawable.ic_menu_close_clear_cancel,
				res.getString(R.string.list_host_disconnect),
				PendingIntent.getActivity(
						context,
						ONLINE_DISCONNECT_NOTIFICATION,
						disconnectIntent,
						0));

		return builder.build();
	}

	public void showActivityNotification(Service context, HostBean host) {
		getNotificationManager(context).notify(ACTIVITY_NOTIFICATION, newActivityNotification(context, host));
	}

	public void hideActivityNotification(Service context) {
		getNotificationManager(context).cancel(ACTIVITY_NOTIFICATION);
	}

	public abstract void showRunningNotification(Service context);
	public abstract void hideRunningNotification(Service context);

	private static class PreEclair extends ConnectionNotifier {
		private static final Class<?>[] setForegroundSignature = new Class[] {boolean.class};
		private Method setForeground = null;

		private static class Holder {
			private static final PreEclair sInstance = new PreEclair();
		}

		public PreEclair() {
			try {
				setForeground = Service.class.getMethod("setForeground", setForegroundSignature);
			} catch (Exception e) {
			}
		}

		@Override
		public void showRunningNotification(Service context) {
			if (setForeground != null) {
				Object[] setForegroundArgs = new Object[1];
				setForegroundArgs[0] = Boolean.TRUE;
				try {
					setForeground.invoke(context, setForegroundArgs);
				} catch (InvocationTargetException e) {
				} catch (IllegalAccessException e) {
				}
				getNotificationManager(context).notify(ONLINE_NOTIFICATION, newRunningNotification(context));
			}
		}

		@Override
		public void hideRunningNotification(Service context) {
			if (setForeground != null) {
				Object[] setForegroundArgs = new Object[1];
				setForegroundArgs[0] = Boolean.FALSE;
				try {
					setForeground.invoke(context, setForegroundArgs);
				} catch (InvocationTargetException e) {
				} catch (IllegalAccessException e) {
				}
				getNotificationManager(context).cancel(ONLINE_NOTIFICATION);
			}
		}
	}

	@TargetApi(5)
	private static class EclairAndBeyond extends ConnectionNotifier {
		private static class Holder {
			private static final EclairAndBeyond sInstance = new EclairAndBeyond();
		}

		@Override
		public void showRunningNotification(Service context) {
			context.startForeground(ONLINE_NOTIFICATION, newRunningNotification(context));
		}

		@Override
		public void hideRunningNotification(Service context) {
			context.stopForeground(true);
		}
	}
}
