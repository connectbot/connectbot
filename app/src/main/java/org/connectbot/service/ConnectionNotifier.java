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
import org.connectbot.R;
import org.connectbot.bean.HostBean;
import org.connectbot.util.HostDatabase;
import org.connectbot.util.PreferenceConstants;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;

/**
 * @author Kenny Root
 *
 * Based on the concept from jasta's blog post.
 */
public abstract class ConnectionNotifier {
	private static final int ONLINE_NOTIFICATION = 1;
	private static final int ACTIVITY_NOTIFICATION = 2;

	public static ConnectionNotifier getInstance() {
		if (PreferenceConstants.PRE_ECLAIR)
			return PreEclair.Holder.sInstance;
		else
			return EclairAndBeyond.Holder.sInstance;
	}

	protected NotificationManager getNotificationManager(Context context) {
		return (NotificationManager)context.getSystemService(Context.NOTIFICATION_SERVICE);
	}

	protected Notification newNotification(Context context) {
		Notification notification = new Notification();
		notification.icon = R.drawable.notification_icon;
		notification.when = System.currentTimeMillis();

		return notification;
	}

	protected Notification newActivityNotification(Context context, HostBean host) {
		Notification notification = newNotification(context);

		Resources res = context.getResources();

		String contentText = res.getString(
				R.string.notification_text, host.getNickname());

		Intent notificationIntent = new Intent(context, ConsoleActivity.class);
		notificationIntent.setAction("android.intent.action.VIEW");
		notificationIntent.setData(host.getUri());

		PendingIntent contentIntent = PendingIntent.getActivity(context, 0,
				notificationIntent, 0);

		notification.setLatestEventInfo(context, res.getString(R.string.app_name), contentText, contentIntent);

		notification.flags = Notification.FLAG_AUTO_CANCEL;

		notification.flags |= Notification.DEFAULT_LIGHTS;
		if (HostDatabase.COLOR_RED.equals(host.getColor()))
			notification.ledARGB = Color.RED;
		else if (HostDatabase.COLOR_GREEN.equals(host.getColor()))
			notification.ledARGB = Color.GREEN;
		else if (HostDatabase.COLOR_BLUE.equals(host.getColor()))
			notification.ledARGB = Color.BLUE;
		else
			notification.ledARGB = Color.WHITE;
		notification.ledOnMS = 300;
		notification.ledOffMS = 1000;
		notification.flags |= Notification.FLAG_SHOW_LIGHTS;

		return notification;
	}

	protected Notification newRunningNotification(Context context) {
		Notification notification = newNotification(context);

		notification.flags = Notification.FLAG_ONGOING_EVENT
				| Notification.FLAG_NO_CLEAR;
		notification.when = 0;

		notification.contentIntent = PendingIntent.getActivity(context,
				ONLINE_NOTIFICATION,
				new Intent(context, ConsoleActivity.class), 0);

		Resources res = context.getResources();

		notification.setLatestEventInfo(context,
				res.getString(R.string.app_name),
				res.getString(R.string.app_is_running),
				notification.contentIntent);

		return notification;
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
