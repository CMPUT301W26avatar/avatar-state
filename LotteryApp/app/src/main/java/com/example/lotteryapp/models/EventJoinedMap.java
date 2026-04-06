package com.example.lotteryapp.models;

/**
 * Join model recording the location from which a user joined a specific event's waitlist.
 * <p>
 * Used to track geo-constraint compliance: when a user joins an event that has a geo-constraint,
 * their location at the time of joining is stored here so it can be verified against the
 * event's {@link EventAddress#getRadiusKm() radius}.
 * </p>
 * Stored under: {@code /events/{eventId}/joined_map/{uid}}
 */
public class EventJoinedMap {
    private String eventId;
    private UserAddress joinedFrom;
    private Long joinedAtMs;

    /**
     * Required no-arg constructor for Firestore deserialization.
     */
    public EventJoinedMap() {
        // Firestore constructor
    }

    /**
     * Creates an EventJoinedMap without a join timestamp.
     *
     * @param eventId    the ID of the event the user joined
     * @param joinedFrom the user's location at the time of joining
     */
    public EventJoinedMap(String eventId, UserAddress joinedFrom) {
        this.eventId = eventId;
        this.joinedFrom = joinedFrom;
    }

    /**
     * Creates an EventJoinedMap with an explicit join timestamp.
     *
     * @param eventId    the ID of the event the user joined
     * @param joinedFrom the user's location at the time of joining
     * @param joinedAtMs the epoch time in milliseconds when the user joined
     */
    public EventJoinedMap(String eventId, UserAddress joinedFrom, Long joinedAtMs) {
        this.eventId = eventId;
        this.joinedFrom = joinedFrom;
        this.joinedAtMs = joinedAtMs;
    }

    /**
     * Returns the ID of the event associated with this record.
     *
     * @return the event ID
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Sets the ID of the event associated with this record.
     *
     * @param eventId the event ID
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /**
     * Returns the user's address at the time they joined the event.
     *
     * @return the {@link UserAddress} captured at join time, or {@code null} if not recorded
     */
    public UserAddress getJoinedFrom() {
        return joinedFrom;
    }

    /**
     * Sets the user's address at the time they joined the event.
     *
     * @param joinedFrom the {@link UserAddress} to record
     */
    public void setJoinedFrom(UserAddress joinedFrom) {
        this.joinedFrom = joinedFrom;
    }

    /**
     * Returns the epoch time in milliseconds when the user joined.
     *
     * @return the join timestamp in ms, or {@code null} if not recorded
     */
    public Long getJoinedAtMs() {
        return joinedAtMs;
    }

    /**
     * Sets the epoch time in milliseconds when the user joined.
     *
     * @param joinedAtMs the join timestamp in ms
     */
    public void setJoinedAtMs(Long joinedAtMs) {
        this.joinedAtMs = joinedAtMs;
    }
}
