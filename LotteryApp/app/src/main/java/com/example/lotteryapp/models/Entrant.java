package com.example.lotteryapp.models;

/**
 * Model class representing the relationship between a User and an Event.
 */
public class Entrant {
    private final String entrantId;
    private final String eventId;

    private EntrantStatus entrantStatus;

    /** Lifecycle status of an entrant with respect to a single event. */
    public enum EntrantStatus {
        /** Selected from the waitlist; invitation sent but not yet acted upon. */
        INVITED,
        /** On the waitlist but not selected in the current lottery draw. */
        NOT_INVITED,
        /** Was selected (invited) but the entrant declined the invitation. */
        DECLINED,
        /** On the waitlist; lottery selection has not yet occurred. */
        WAITLISTED,
        /** Accepted the invitation; confirmed participant in the event. */
        ENROLLED,
        /** Organizer cancelled the entrant's invitation. */
        CANCELLED
    }

    /**
     * Creates a new Entrant record linking a user to an event with the given initial status.
     *
     * @param entrantId the unique identifier of the user acting as the entrant;
     *                  must be non-null and non-blank
     * @param eventId   the unique identifier of the event;
     *                  must be non-null and non-blank
     * @param status    the initial {@link EntrantStatus} for this entrant
     * @throws IllegalArgumentException if either {@code entrantId} or {@code eventId} is null or blank
     */
    public Entrant(String entrantId, String eventId, EntrantStatus status) {
        if (eventId == null || eventId.trim().isEmpty()) {
            throw new IllegalArgumentException("eventId required");
        }
        if (entrantId == null || entrantId.trim().isEmpty()) {
            throw new IllegalArgumentException("entrantId required");
        }
        this.entrantId = entrantId;
        this.eventId = eventId;
        this.entrantStatus = status;
    }

    /**
     * Returns the unique identifier of the user (entrant) in this relationship.
     *
     * @return the entrant's user ID
     */
    public String getEntrantId() {
        return entrantId;
    }

    /**
     * Returns the unique identifier of the event in this relationship.
     *
     * @return the event ID
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Returns the current lifecycle status of this entrant.
     *
     * @return the {@link EntrantStatus}
     */
    public EntrantStatus getStatus() {
        return entrantStatus;
    }

    /**
     * Updates the lifecycle status of this entrant.
     *
     * @param status the new {@link EntrantStatus}
     */
    public void setStatus(EntrantStatus status) {
        this.entrantStatus = status;
    }
}
