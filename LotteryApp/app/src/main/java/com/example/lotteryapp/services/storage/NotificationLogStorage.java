package com.example.lotteryapp.services.storage;

import static com.example.lotteryapp.models.NotificationLog.NotificationStatus.PENDING;

import com.example.lotteryapp.models.NotificationLog;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.Query;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Storage logic for notification log entries
 * R & W to notifications collection
 *
 * Call logNotification()
 *      wherever an organizer sends a notification to entrants
 * Call getAllNotificationLogs()
 *      from admin log viewer.
 * Access through ServiceLocator.getNotificationLogStorage().
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
        data.put("status", PENDING);
        data.put("timestamp", FieldValue.serverTimestamp());
        data.put("updatedAt", FieldValue.serverTimestamp());

        db.collection(COLLECTION)
                .add(data)
                .addOnSuccessListener(ref -> onSuccess.onSuccess(ref.getId()))
                .addOnFailureListener(onFailure);
    }

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
     * Gets notifications for one user, newest first.
     * This is the method HomeFragment should use for the red popup / swipeable notification UI.
     */
    public void getPendingNotificationsForUser(
            String userId,
            OnSuccessListener<List<NotificationLog>> onSuccess,
            OnFailureListener onFailure
    ) {
        db.collection(COLLECTION)
                .whereEqualTo("userId", userId)
                .whereEqualTo("status", PENDING)
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
}