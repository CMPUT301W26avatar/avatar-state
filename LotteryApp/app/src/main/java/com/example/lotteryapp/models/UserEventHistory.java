package com.example.lotteryapp.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Simple history record for a user's involvement in a single event action.
 * Stored under: /users/{userId}/event_history/{eventId}
 *
 * The Entrant model relates events to users, and it is a two-way association,
 *  but the firebase schema for Entrant is Event-centric, meaning we have to dip into the
 *      event collection for every possible event to determine the event history of a User.
 * This is also two-way assoc. rel. b/w Event and User but is User-centric in firebase, so we can grab the history all from one subcollection.
 */
public class UserEventHistory {

    public enum HistoryStatus {
        WAITLISTED, // entrant joins waitlist
        LEFT_WAITLIST, // entrant leaves waitlist
        NOT_SELECTED, // lost the the current round of lottery selection
        INVITED, // organizer sends invite (lottery selection)
        ENROLLED, // entrant accepts invite
        CANCELLED, // organizer cancels invite
        DECLINED, // entrant declines invite
        UNENROLLED
    }
    private String userId;
    private String eventId;
    private String status;
    private Long updatedAtMs;

    public UserEventHistory() {
        // empty constructor for Firestore
    }

    public UserEventHistory(@NonNull String userId, @NonNull String eventId, @NonNull HistoryStatus status, @Nullable Long updatedAtMs) {
        this.userId = userId;
        this.eventId = eventId;
        this.status = status.name();
        this.updatedAtMs = updatedAtMs != null ? updatedAtMs : System.currentTimeMillis(); // nullable for beginning new history
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getUpdatedAtMs() {
        return updatedAtMs;
    }

    public void setUpdatedAtMs(Long updatedAtMs) {
        this.updatedAtMs = updatedAtMs;
    }
}