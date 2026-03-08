package com.example.lotteryapp;

public class Entrant {
    private final String entrantId;
    private final String eventId;

    private EntrantStatus entrantStatus;

    public enum EntrantStatus {
        INVITED, // not in pool or waitlist, but invited to sign up
        CANCELLED, // selected, but user denied selection
        WAITLISTED, // not in pool, in waitlist
        ENROLLED, // in pool, draw has yet to happen
        SELECTED, // selected from pool
        NOT_SELECTED, // not selected from pool
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
