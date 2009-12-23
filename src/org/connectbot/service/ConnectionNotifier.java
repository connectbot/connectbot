/**
 *
 */
package org.connectbot.service;

import org.connectbot.ConsoleActivity;
import org.connectbot.R;
import org.connectbot.bean.HostBean;
import org.connectbot.util.PreferenceConstants;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;

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
		private static class Holder {
			private static final PreEclair sInstance = new PreEclair();
		}

		@Override
		public void showRunningNotification(Service context) {
			context.setForeground(true);
			getNotificationManager(context).notify(ONLINE_NOTIFICATION, newRunningNotification(context));
		}

		@Override
		public void hideRunningNotification(Service context) {
			context.setForeground(false);
			getNotificationManager(context).cancel(ONLINE_NOTIFICATION);
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
