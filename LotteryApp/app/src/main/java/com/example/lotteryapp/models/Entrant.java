package com.example.lotteryapp.models;

/** Model class for a User relationship with Event
 * - A user can only exist as an Entrant one time in the entirety of the Event.
 * - An Entrant has its status (EntrantStatus) cycled through as they perform actions in the app
 *      i.e. User selects "Accept Invitation": goes from INVITED -> ENROLLED, etc.
 * - No setters for eventId or entrantId!
 */


public class Entrant {
    private final String entrantId;
    private final String eventId;

    private EntrantStatus entrantStatus;

    public enum EntrantStatus {
        INVITED, // in waitlist, selected
        NOT_INVITED, // in waitlist, not selected
        DECLINED, // in waitlist, selected, declined
        WAITLISTED, // in waitlist, selection has yet to happen
        ENROLLED, // won the lottery, enrolled in the Event
        CANCELLED
    }

    public Entrant(String entrantId, String eventId, EntrantStatus status) {
        if (eventId == null || eventId.trim().isEmpty()) {
            throw new IllegalArgumentException("eventId required");
        }
        if (entrantId == null || entrantId.trim().isEmpty()) {
            throw new IllegalArgumentException("entrantId required");
        }
        this.entrantId = entrantId;
        this.eventId = eventId;
        this.entrantStatus = status; // IN_POOL, WAITLISTED, INVITED, SELECTED, NOT_SELECTED
    }

    public String getEntrantId() {
        return entrantId;
    }

    public String getEventId() {
        return eventId;
    }

    public EntrantStatus getStatus() {
        return entrantStatus;
    }

    public void setStatus(EntrantStatus status) {
        this.entrantStatus = status;
    }
}
