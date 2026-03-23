package com.example.lotteryapp.models;

/**
 * Join model representing one entrant's joined-from location for one event
 * Stored under:
 *      /events/{eventId}/joined_map/{uid}
 */
public class EventJoinedMap {
    private String eventId;
    private UserAddress joinedFrom;
    private Long joinedAtMs;

    public EventJoinedMap() {
        // Firestore constructor
    }

    public EventJoinedMap(String eventId, UserAddress joinedFrom) {
        this.eventId = eventId;
        this.joinedFrom = joinedFrom;
    }

    public EventJoinedMap(String eventId, UserAddress joinedFrom, Long joinedAtMs) {
        this.eventId = eventId;
        this.joinedFrom = joinedFrom;
        this.joinedAtMs = joinedAtMs;
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public UserAddress getJoinedFrom() {
        return joinedFrom;
    }

    public void setJoinedFrom(UserAddress joinedFrom) {
        this.joinedFrom = joinedFrom;
    }

    public Long getJoinedAtMs() {
        return joinedAtMs;
    }

    public void setJoinedAtMs(Long joinedAtMs) {
        this.joinedAtMs = joinedAtMs;
    }
}