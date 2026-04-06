package com.example.lotteryapp.models;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Simple history record capturing a user's involvement in a single event action.
 * <p>
 * The {@link Entrant} model relates events to users in a two-way association, but its
 * Firestore schema is Event-centric — reconstructing a user's full history requires
 * sweeping the entire events collection. This model is User-centric: all history entries
 * for a user are stored under one sub-collection, enabling efficient per-user queries.
 * </p>
 * Stored under: {@code /users/{userId}/event_history/{eventId}}
 */
public class UserEventHistory {

    /** Possible states for a user's involvement in an event's lifecycle. */
    public enum HistoryStatus {
        /** Entrant has joined the waitlist. */
        WAITLISTED,
        /** Entrant has left the waitlist voluntarily. */
        LEFT_WAITLIST,
        /** Entrant was not selected in the current lottery round. */
        NOT_SELECTED,
        /** Organizer sent an invitation (lottery selection). */
        INVITED,
        /** Entrant accepted the invitation. */
        ENROLLED,
        /** Organizer cancelled the entrant's invitation. */
        CANCELLED,
        /** Entrant declined the invitation. */
        DECLINED,
        /** Entrant was unenrolled from the event. */
        UNENROLLED
    }

    private String userId;
    private String eventId;
    private String status;
    private Long updatedAtMs;

    /**
     * Required no-arg constructor for Firestore deserialization.
     */
    public UserEventHistory() {
        // empty constructor for Firestore
    }

    /**
     * Creates a history entry for a user's participation action in an event.
     *
     * @param userId      the unique identifier of the user; must be non-null
     * @param eventId     the unique identifier of the event; must be non-null
     * @param status      the {@link HistoryStatus} describing the action taken; must be non-null
     * @param updatedAtMs the epoch time in ms when this status was recorded;
     *                    if {@code null}, the current system time is used
     */
    public UserEventHistory(@NonNull String userId, @NonNull String eventId,
                            @NonNull HistoryStatus status, @Nullable Long updatedAtMs) {
        this.userId = userId;
        this.eventId = eventId;
        this.status = status.name();
        this.updatedAtMs = updatedAtMs != null ? updatedAtMs : System.currentTimeMillis();
    }

    /**
     * Returns the unique identifier of the user this record belongs to.
     *
     * @return the user ID
     */
    public String getUserId() {
        return userId;
    }

    /**
     * Sets the unique identifier of the user this record belongs to.
     *
     * @param userId the user ID
     */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /**
     * Returns the unique identifier of the event this record relates to.
     *
     * @return the event ID
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Sets the unique identifier of the event this record relates to.
     *
     * @param eventId the event ID
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /**
     * Returns the serialised status string (the {@link HistoryStatus} enum name).
     *
     * @return the status string (e.g. {@code "WAITLISTED"})
     */
    public String getStatus() {
        return status;
    }

    /**
     * Sets the status string. Prefer using the constructor with a {@link HistoryStatus}
     * value; this setter exists primarily for Firestore deserialization.
     *
     * @param status the status string
     */
    public void setStatus(String status) {
        this.status = status;
    }

    /**
     * Returns the epoch time in milliseconds when this status was last recorded.
     *
     * @return the update timestamp in ms, or {@code null} if not recorded
     */
    public Long getUpdatedAtMs() {
        return updatedAtMs;
    }

    /**
     * Sets the epoch time in milliseconds when this status was last recorded.
     *
     * @param updatedAtMs the update timestamp in ms
     */
    public void setUpdatedAtMs(Long updatedAtMs) {
        this.updatedAtMs = updatedAtMs;
    }
}
