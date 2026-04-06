package com.example.lotteryapp.models;

import com.google.firebase.Timestamp;

/**
 * Model class for a notification log entry.
 * <ul>
 *   <li>Stored in the top-level {@code notifications} Firestore collection.</li>
 *   <li>Created any time an organizer sends a notification to an entrant.</li>
 *   <li>Tracks both the organizer's intent ({@link NotificationType}) and
 *       the entrant's response ({@link NotificationStatus}).</li>
 * </ul>
 */
public class NotificationLog {

    private String id;
    private String eventId;
    private String organizerId;
    private String userId;
    private String title;
    private String message;
    private NotificationType type;
    private NotificationStatus status;
    private Timestamp timestamp;

    /** Categorises the reason an organizer sent a notification. */
    public enum NotificationType {
        /** Entrant was selected (won) in the lottery draw; actionable — accept or decline. */
        WON_LOTTERY,
        /** Entrant was not selected (lost) in the lottery draw. */
        LOST_LOTTERY,
        /** Generic organizer message to an entrant. */
        MESSAGE,
        /** Direct (private) invitation to an event. */
        PRIVATE_INVITATION,
        /** Invitation to become a co-organizer of an event. */
        COORGANIZER_INVITATION,
        /** Announcement broadcast to all entrants of an event. */
        EVENT_ANNOUNCEMENT,
    }

    /** Tracks the entrant's response to a notification. */
    public enum NotificationStatus {
        /** The entrant has not yet acted on the notification. */
        PENDING,
        /** The entrant accepted an invitation. */
        ACCEPTED,
        /** The entrant declined an invitation. */
        DECLINED,
        /** The entrant read a non-actionable notification. */
        READ
    }

    /**
     * Required no-arg constructor for Firestore deserialization.
     */
    public NotificationLog() {}

    /**
     * Creates a NotificationLog from raw field values (unverified event data).
     *
     * @param eventId     ID of the event this notification relates to
     * @param organizerId ID of the organizer who sent the notification
     * @param userId      ID of the user receiving the notification
     * @param title       title of the notification
     * @param message     body message of the notification
     * @param type        the {@link NotificationType} categorising the notification's purpose
     * @param status      the initial {@link NotificationStatus}
     * @param timestamp   server timestamp when the notification was created
     */
    public NotificationLog(String eventId, String organizerId, String userId, String title,
                           String message, NotificationType type, NotificationStatus status, Timestamp timestamp) {
        this.eventId = eventId;
        this.organizerId = organizerId;
        this.userId = userId;
        this.title = title;
        this.message = message;
        this.type = type;
        this.status = status;
        this.timestamp = timestamp;
    }

    /**
     * Creates a NotificationLog from a verified {@link Event} object.
     * The {@code eventId} and {@code organizerId} are read directly from the event.
     *
     * @param event     the verified event this notification relates to
     * @param userId    ID of the user receiving the notification
     * @param title     title of the notification
     * @param message   body message of the notification
     * @param type      the {@link NotificationType} categorising the notification's purpose
     * @param status    the initial {@link NotificationStatus}
     * @param timestamp server timestamp when the notification was created
     */
    public NotificationLog(Event event, String userId, String title,
                           String message, NotificationType type, NotificationStatus status, Timestamp timestamp) {
        this.eventId = event.getEventId();
        this.organizerId = event.getOrganizerId();
        this.userId = userId;
        this.title = title;
        this.message = message;
        this.type = type;
        this.status = status;
        this.timestamp = timestamp;
    }

    /**
     * Returns the Firestore document ID for this notification log entry.
     *
     * @return the document ID, or {@code null} if not yet persisted
     */
    public String getId() { return id; }

    /**
     * Sets the Firestore document ID for this notification log entry.
     *
     * @param id the document ID
     */
    public void setId(String id) { this.id = id; }

    /**
     * Returns the ID of the event this notification relates to.
     *
     * @return the event ID
     */
    public String getEventId() { return eventId; }

    /**
     * Sets the ID of the event this notification relates to.
     *
     * @param eventId the event ID
     */
    public void setEventId(String eventId) { this.eventId = eventId; }

    /**
     * Returns the ID of the organizer who sent this notification.
     *
     * @return the organizer's user ID
     */
    public String getOrganizerId() { return organizerId; }

    /**
     * Sets the ID of the organizer who sent this notification.
     *
     * @param organizerId the organizer's user ID
     */
    public void setOrganizerId(String organizerId) { this.organizerId = organizerId; }

    /**
     * Returns the ID of the user receiving this notification.
     *
     * @return the recipient's user ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Sets the ID of the user receiving this notification.
     *
     * @param userId the recipient's user ID
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Returns the notification title.
     *
     * @return the title string
     */
    public String getTitle() { return title; }

    /**
     * Sets the notification title.
     *
     * @param title the title string
     */
    public void setTitle(String title) { this.title = title; }

    /**
     * Returns the notification body message.
     *
     * @return the message string
     */
    public String getMessage() { return message; }

    /**
     * Sets the notification body message.
     *
     * @param message the message string
     */
    public void setMessage(String message) { this.message = message; }

    /**
     * Returns the type of this notification, indicating why it was sent.
     *
     * @return the {@link NotificationType}
     */
    public NotificationType getType() { return type; }

    /**
     * Sets the type of this notification.
     *
     * @param type the {@link NotificationType}
     */
    public void setType(NotificationType type) { this.type = type; }

    /**
     * Returns the current status of this notification from the recipient's perspective.
     *
     * @return the {@link NotificationStatus}
     */
    public NotificationStatus getStatus() { return status; }

    /**
     * Sets the status of this notification from the recipient's perspective.
     *
     * @param status the {@link NotificationStatus}
     */
    public void setStatus(NotificationStatus status) { this.status = status; }

    /**
     * Returns the server timestamp when this notification was created.
     *
     * @return the Firestore {@link Timestamp}
     */
    public Timestamp getTimestamp() { return timestamp; }

    /**
     * Sets the server timestamp for this notification.
     *
     * @param timestamp the Firestore {@link Timestamp}
     */
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }
}
