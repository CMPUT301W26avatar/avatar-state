package com.example.lotteryapp.services.storage;

import static com.example.lotteryapp.models.NotificationLog.NotificationStatus.PENDING;

import com.example.lotteryapp.models.NotificationLog;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.WriteBatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Storage logic for notification log entries
 *      logNotification() - saving a new notification
 *      getAllNotifications() - admin view all notifications
 *      getPendingNotificationsForUser() - notification banner in HomeFragment
 */
public class NotificationLogStorage {

    private static final String COLLECTION = "notifications";
    private final FirebaseFirestore db;

    /**
     * Makes a NotificationLogStorage from given firestore instance.
     *
     * @param db the firestore database instance
     */
    public NotificationLogStorage(FirebaseFirestore db) {
        this.db = db;
    }

    /**
     * Logs a notification
     * Call wherever organizer sends a notification to entrant
     *
     * @param eventId     ID of the event the notification relates to
     * @param organizerId ID of the organizer sending the notif
     * @param title       title of the notification
     * @param message     message to convey through notif
     * @param type        either "invitation", "lottery_result", "message", "admin"
     * @param onSuccess   Called on success
     * @param onFailure   Called on failure
     */
    public void logNotification(
            String eventId,
            String organizerId,
            String userId,
            String title,
            String message,
            NotificationLog.NotificationType type,
            OnSuccessListener<String> onSuccess,
            OnFailureListener onFailure
    ) {
        Map<String, Object> data = new HashMap<>();
        data.put("eventId", eventId);
        data.put("organizerId", organizerId);
        data.put("userId", userId);
        data.put("title", title);
        data.put("message", message);
        data.put("type", type == null ? null : type.name());
        data.put("status", PENDING.name());
        data.put("timestamp", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());

        db.collection(COLLECTION)
                .add(data)
                .addOnSuccessListener(ref -> onSuccess.onSuccess(ref.getId()))
                .addOnFailureListener(onFailure);
    }

    /**
     * Sends an announcement to multiple users at once
     *      Creates one NotificationLog entry per user using a Firestore batch write
     */
    public void logAnnouncementToUsers(
            String eventId,
            String organizerId,
            List<String> userIds,
            String title,
            String message,
            NotificationLog.NotificationType type,
            OnSuccessListener<String> onSuccess,
            OnFailureListener onFailure
    ) {
        if (userIds == null || userIds.isEmpty()) {
            onSuccess.onSuccess("0");
            return;
        }

        WriteBatch batch = db.batch();
        CollectionReference notifications = db.collection(COLLECTION);

        for (String userId : userIds) {
            DocumentReference docRef = notifications.document();
            Map<String, Object> data = new HashMap<>();
            data.put("eventId", eventId);
            data.put("organizerId", organizerId);
            data.put("userId", userId);
            data.put("title", title);
            data.put("message", message);
            data.put("type", type == null ? null : type.name());
            data.put("status", PENDING.name());
            data.put("timestamp", FieldValue.serverTimestamp());
            data.put("updatedAt", FieldValue.serverTimestamp());

            batch.set(docRef, data);
        }

        batch.commit()
                .addOnSuccessListener(unused -> onSuccess.onSuccess(String.valueOf(userIds.size())))
                .addOnFailureListener(onFailure);
    }

    /**
     * Update the status of a notification after a user has accepted or declined it
     *      takes in the new status of the notification to store it
     */
    public void updateNotificationStatus(
            String notificationId,
            String status,
            OnSuccessListener<Void> onSuccess,
            OnFailureListener onFailure
    ) {
        if (notificationId == null || notificationId.trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("notificationId required"));
            return;
        }

        if (status == null || status.trim().isEmpty()) {
            onFailure.onFailure(new IllegalArgumentException("status required"));
            return;
        }

        Map<String, Object> updates = new HashMap<>();
        updates.put("status", status);
        updates.put("updatedAt", FieldValue.serverTimestamp());

        db.collection(COLLECTION)
                .document(notificationId)
                .update(updates)
                .addOnSuccessListener(onSuccess)
                .addOnFailureListener(onFailure);
    }

    /**
     * Gets all notification log entries (new first)
     * Intended for admin notification log viewer
     *
     * @param onSuccess called with list of NotificationLog entries on success
     * @param onFailure called with exception on failure
     */
    public void getAllNotificationLogs(
            OnSuccessListener<List<NotificationLog>> onSuccess,
            OnFailureListener onFailure
    ) {
        db.collection(COLLECTION)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<NotificationLog> logs = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshot) {
                        NotificationLog log = doc.toObject(NotificationLog.class);
                        log.setId(doc.getId());
                        logs.add(log);
                    }
                    onSuccess.onSuccess(logs);
                })
                .addOnFailureListener(onFailure);
    }

    /**
     * Gets notifications for one user, newest first
     *      only returns pending notifications
     *          accepted or declined notifications are deleted
     */
    public void getPendingNotificationsForUser(
            String userId,
            OnSuccessListener<List<NotificationLog>> onSuccess,
            OnFailureListener onFailure
    ) {
        db.collection(COLLECTION)
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", PENDING.name())
                .get()
                .addOnSuccessListener(snapshot -> {
                    List<NotificationLog> logs = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : snapshot) {
                        try {
                            NotificationLog log = new NotificationLog();
                            log.setId(doc.getId());
                            log.setEventId(doc.getString("eventId"));
                            log.setOrganizerId(doc.getString("organizerId"));
                            log.setUserId(doc.getString("userId"));
                            log.setTitle(doc.getString("title"));
                            log.setMessage(doc.getString("message"));
                            log.setTimestamp(doc.getTimestamp("timestamp"));

                            Object rawType = doc.get("type");
                            if (rawType instanceof String) {
                                try {
                                    log.setType(NotificationLog.NotificationType.valueOf((String) rawType));
                                } catch (IllegalArgumentException ignored) {
                                    log.setType(null);
                                }
                            } else {
                                log.setType(null);
                            }

                            logs.add(log);
                        } catch (Exception e) {
                            if (onFailure != null) {
                                onFailure.onFailure(e);
                            }
                            return;
                        }
                    }

                    logs.sort((a, b) -> {
                        if (a.getTimestamp() == null && b.getTimestamp() == null) return 0;
                        if (a.getTimestamp() == null) return 1;
                        if (b.getTimestamp() == null) return -1;
                        return b.getTimestamp().compareTo(a.getTimestamp());
                    });

                    onSuccess.onSuccess(logs);
                })
                .addOnFailureListener(onFailure);
    }

    public void getPrivateInviteEventIdsForUser(
            String entrantId,
            OnSuccessListener<List<String>> onSuccess,
            OnFailureListener onFailure
    ) {
        db.collection("notifications")   // adjust if your collection name differs
                .whereEqualTo("userId", entrantId)
                .whereEqualTo("type", "PRIVATE_INVITATION")
                .whereEqualTo("status", "PENDING")
                .get()
                .addOnSuccessListener(querySnapshot -> {
                    List<String> eventIds = new ArrayList<>();

                    for (QueryDocumentSnapshot doc : querySnapshot) {
                        String eventId = doc.getString("eventId");

                        if (eventId != null
                                && !eventId.trim().isEmpty()
                                && !eventIds.contains(eventId)) {
                            eventIds.add(eventId);
                        }
                    }

                    onSuccess.onSuccess(eventIds);
                })
                .addOnFailureListener(onFailure);
    }
}