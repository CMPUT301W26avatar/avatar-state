package com.example.lotteryapp.models;

import com.google.firebase.Timestamp;

/** Model class for a notifciation log entry
 * - stored in in notifications collection
 * - created anytmie a organizer sends notification to entrant
 */
public class NotificationLog {

    private String id;
    private String eventId;
    private String organizerId;
    private String title;
    private String message;
    private String type;
    private Timestamp timestamp;

    public NotificationLog() {}

    /**
     * make NotificationLog
     *
     * @param eventId     id of event that this notification is for
     * @param organizerId id of organizer who sent notif.
     * @param title       title of notif.
     * @param message     message notif. is conveying
     * @param type        either "invitation", "lottery_result", "message", "admin"
     * @param timestamp   timestamp of notif.
     */
    public NotificationLog(String eventId, String organizerId, String title,
                           String message, String type, Timestamp timestamp) {
        this.eventId = eventId;
        this.organizerId = organizerId;
        this.title = title;
        this.message = message;
        this.type = type;
        this.timestamp = timestamp;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }
    public String getOrganizerId() { return organizerId; }
    public void setOrganizerId(String organizerId) { this.organizerId = organizerId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Timestamp getTimestamp() { return timestamp; }
    public void setTimestamp(Timestamp timestamp) { this.timestamp = timestamp; }
}
