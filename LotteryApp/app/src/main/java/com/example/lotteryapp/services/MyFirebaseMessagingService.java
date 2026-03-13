package com.example.lotteryapp.services;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.lotteryapp.R;
import com.example.lotteryapp.activities.MainActivity;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

/**
 * Service to handle Firebase Cloud Messaging (FCM) notifications.
 * 
 * --- DEVELOPER GUIDE: SENDING NOTIFICATIONS ---
 * To send a notification that respects user category toggles, you MUST include a "type"
 * field in the FCM data payload.
 * 
 * Valid types:
 * - "invitation": Checked against 'Event Invitations' toggle
 * - "lottery_result": Checked against 'Lottery Results' toggle
 * - "message": Checked against 'Messages from Organizers' toggle
 * - "admin": Checked against 'System Alerts' toggle
 */
public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    private static final String PREFS_NAME = "notification_prefs";
    private static final String CHANNEL_ID = "lottery_notifications";

    /**
     * Handles an incoming Firebase notification message
     *      extracts notification title and body from either the noti. or noti. data
     */
    @Override
    public void onMessageReceived(@NonNull RemoteMessage remoteMessage) {
        super.onMessageReceived(remoteMessage);
        
        String title = null;
        String body = null;

        if (remoteMessage.getNotification() != null) {
            title = remoteMessage.getNotification().getTitle();
            body = remoteMessage.getNotification().getBody();
        }

        if (remoteMessage.getData().size() > 0) {
            String type = remoteMessage.getData().get("type");
            
            if (isNotificationEnabled(type)) {
                if (title == null) {
                    title = remoteMessage.getData().get("title");
                }
                if (body == null) {
                    body = remoteMessage.getData().get("body");
                }
                
                // TODO: extract eventId or other data from payload to implement deep linking
                sendNotification(title, body, remoteMessage.getData().get("eventId"));
            }
        }
        else if (remoteMessage.getNotification() != null) {
            if (isNotificationEnabled(null)) {
                sendNotification(title, body, null);
            }
        }
    }

    /**
     * Called when a new Firebase noti. registration token is generated
     */
    @Override
    public void onNewToken(@NonNull String token) {
        super.onNewToken(token);
        String uid = ServiceLocator.uid();
        if (uid != null) {
            ServiceLocator.getUserStorage().updateFcmToken(uid, token);
        }
    }

    /**
     * Checks whether a notification of the given type is enabled in the users notification preferences
     */
    private boolean isNotificationEnabled(String type) {
        // TODO: check system-level NotificationManagerCompat.from(this).areNotificationsEnabled()
        // to stay perfectly synchronous with system settings even if SharedPreferences isn't updated.
        
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        
        if (!prefs.getBoolean("notifications_all_enabled", true)) {
            return false;
        }

        if ("invitation".equals(type)) {
            return prefs.getBoolean("notifications_invitations", true);
        }
        else if ("lottery_result".equals(type)) {
            return prefs.getBoolean("notifications_lottery_results", true);
        }
        else if ("message".equals(type)) {
            return prefs.getBoolean("notifications_messages", true);
        }
        else if ("admin".equals(type)) {
            return prefs.getBoolean("notifications_admin_alerts", true);
        }
        
        return true; 
    }

    /**
     * Sends notification to users
     *      needs title for notification,
     *          string for notification body,
     *          and in the future,
     *          eventId to link to Event
     */
    private void sendNotification(String title, String messageBody, String eventId) {
        if (title == null) {
            title = "New Notification";
        }
        if (messageBody == null) {
            messageBody = "";
        }

        Intent intent = new Intent(this, MainActivity.class);
        // TODO: if eventId is present, configure the intent to open the EventDetailsActivity directly
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent,
                PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_info) // TODO: create and use a dedicated notification icon
                        .setContentTitle(title)
                        .setContentText(messageBody)
                        .setAutoCancel(true)
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_DEFAULT);
                        // TODO: add support for BigTextStyle or BigPictureStyle for rich notifications

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Lottery App Notifications",
                    NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }

        notificationManager.notify((int) System.currentTimeMillis(), notificationBuilder.build());
    }
}
