package com.example.lotteryapp.models;

import java.util.List;
import java.util.UUID;

/** Model class for an Event
 * - Mostly metadata, any of the Count attributes are updated in real time
 * - primary key: event ID, foreign key: user ID (organizerId)
 * - New Event: Pull Event document from Firebase and set EventID as docID.
 * - Only getters and setters
 */
public class Event {
    public String eventId;
    public String organizerId;

    private List<String> coOrganizerIds;
    public EventStatus status;
    public boolean hasDrawnLottery;
    private boolean hasGeoConstraint;
    private boolean privateEvent;

    public int eventCapacity;

    private int enrolledCount;

    public Integer waitlistCapacity; // Integer for optionally set, can be null

    private int waitlistCount;

    private int invitationCount;

    public Long eventDateMs; // milliseconds
    public Long regStartMs; // milliseconds
    public Long regEndMs; // milliseconds

    private EventAddress address;

    private String title;
    private String description;
    private String criteriaGuidelines;
    private String tag;
    private String posterUrl;
    private java.util.List<String> keywords;

    /** Registration and event lifecycle status. */
    public enum EventStatus {
        /** Registration period has not yet started. */
        REG_UPCOMING,
        /** Registration is open; start date has passed. */
        REG_OPEN,
        /** Registration is closed; end date has passed. */
        REG_CLOSED,
        /** Registration is open but the waitlist has reached capacity. */
        REG_FULL,
        /** Event is open; registration period ended and the event still has capacity. */
        EVENT_OPEN,
        /** Event has concluded. */
        EVENT_CLOSED,
        /** Event is open but at capacity. */
        EVENT_FULL,
    }

    /**
     * Required no-arg constructor for Firestore deserialization.
     */
    public Event() {
        // Required for Firebase toObject()
    }

    /**
     * Creates a new Event with the essential capacity fields.
     * A random {@code eventId} is generated automatically.
     * Geo-constraint and private-event flags both default to {@code false}.
     *
     * @param organizerId     the UUID of the user creating this event
     * @param eventCapacity   maximum number of enrolled participants
     * @param waitlistCapacity maximum number of users allowed on the waitlist;
     *                         use {@code null} for an unlimited waitlist
     */
    public Event(String organizerId, int eventCapacity, int waitlistCapacity) {
        this.eventId = UUID.randomUUID().toString();
        this.organizerId = organizerId;
        this.eventCapacity = eventCapacity;
        this.waitlistCapacity = waitlistCapacity;
        this.address = null;
        this.hasDrawnLottery = false;
        this.hasGeoConstraint = false;
        this.privateEvent = false;
    }

    /**
     * Returns the primary key of this event.
     *
     * @return the event ID
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Sets the event's primary key.
     * Only used when pulling a new Event document from Firestore and assigning the document ID.
     *
     * @param eventId the Firestore document ID to use as the event's primary key
     */
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    /**
     * Returns the organizer's user ID (foreign key to the Users collection).
     *
     * @return the organizer's UUID
     */
    public String getOrganizerId() {
        return organizerId;
    }

    /**
     * Returns the list of co-organizer user IDs for this event.
     *
     * @return the list of co-organizer UUIDs, or {@code null} if none have been added
     */
    public List<String> getCoOrganizerIds() {
        return coOrganizerIds;
    }

    /**
     * Sets the list of co-organizer user IDs for this event.
     *
     * @param coOrganizerIds the list of co-organizer UUIDs
     */
    public void setCoOrganizerIds(List<String> coOrganizerIds) {
        this.coOrganizerIds = coOrganizerIds;
    }

    /**
     * Returns {@code true} if this event's registration is currently open.
     *
     * @return {@code true} if {@code status == REG_OPEN}
     */
    public boolean isRegistrationOpen() {
        return this.status == EventStatus.REG_OPEN;
    }

    /**
     * Returns the current lifecycle status of this event.
     *
     * @return the {@link EventStatus}
     */
    public EventStatus getStatus() {
        return status;
    }

    /**
     * Sets the lifecycle status of this event.
     *
     * @param status the new {@link EventStatus}
     */
    public void setStatus(EventStatus status) {
        this.status = status;
    }

    /**
     * Returns {@code true} if the lottery draw has already been run for this event.
     *
     * @return {@code true} if the lottery has been drawn
     */
    public boolean hasDrawnLottery() {
        return hasDrawnLottery;
    }

    /**
     * Sets whether the lottery draw has been run for this event.
     *
     * @param hasDrawnLottery {@code true} after the lottery has been drawn
     */
    public void setHasDrawnLottery(boolean hasDrawnLottery) {
        this.hasDrawnLottery = hasDrawnLottery;
    }

    /**
     * Returns {@code true} if this event has a geo-constraint configured.
     * Note: the constraint is only enforced when {@link #isGeoConstraintEnabled()} also
     * returns {@code true} (i.e. valid coordinates and radius are present).
     *
     * @return {@code true} if the geo-constraint flag is set
     */
    public boolean hasGeoConstraint() {
        return hasGeoConstraint;
    }

    /**
     * Sets whether this event has a geo-constraint.
     *
     * @param hasGeoConstraint {@code true} to enable the geo-constraint flag
     */
    public void setHasGeoConstraint(boolean hasGeoConstraint) {
        this.hasGeoConstraint = hasGeoConstraint;
    }

    /**
     * Returns {@code true} if the geo-constraint is both flagged and fully configured
     * (i.e. the address has valid latitude, longitude, and a positive radius).
     *
     * @return {@code true} if the geo-constraint can be enforced
     */
    public boolean isGeoConstraintEnabled() {
        if (!hasGeoConstraint) {
            return false;
        }

        if (address == null) {
            return false;
        }

        return address.getLatitude() != null
                && address.getLongitude() != null
                && address.getRadiusKm() != null
                && address.getRadiusKm() > 0;
    }

    /**
     * Returns the maximum number of users allowed to participate in the event.
     *
     * @return the event capacity
     */
    public int getEventCapacity() {
        return eventCapacity;
    }

    /**
     * Sets the maximum number of users allowed to participate in the event.
     *
     * @param eventCapacity the event capacity
     */
    public void setEventCapacity(int eventCapacity) {
        this.eventCapacity = eventCapacity;
    }

    /**
     * Returns the current number of users who are confirmed participants
     * (progressed through waitlist → invited → accepted).
     * Requires Firestore to be up to date.
     *
     * @return the enrolled count
     */
    public int getEnrolledCount() {
        return enrolledCount;
    }

    /**
     * Sets the count of confirmed enrolled participants.
     *
     * @param enrolledCount the enrolled count
     */
    public void setEnrolledCount(int enrolledCount) {
        this.enrolledCount = enrolledCount;
    }

    /**
     * Returns the maximum number of users allowed to join the waitlist.
     * Returns {@code null} if the waitlist is unlimited.
     *
     * @return the waitlist capacity, or {@code null} for unlimited
     */
    public Integer getWaitlistCapacity() {
        return waitlistCapacity;
    }

    /**
     * Sets the maximum number of users allowed on the waitlist.
     * Pass {@code null} for an unlimited waitlist.
     *
     * @param waitlistCapacity the waitlist capacity, or {@code null} for unlimited
     */
    public void setWaitlistCapacity(Integer waitlistCapacity) {
        this.waitlistCapacity = waitlistCapacity;
    }

    /**
     * Returns the current number of users on the waitlist.
     * Requires Firestore to be up to date.
     *
     * @return the waitlist count
     */
    public int getWaitlistCount() {
        return waitlistCount;
    }

    /**
     * Sets the current waitlist count.
     *
     * @param waitlistCount the waitlist count
     */
    public void setWaitlistCount(int waitlistCount) {
        this.waitlistCount = waitlistCount;
    }

    /**
     * Sets the number of waitlisted users who have been selected (invited) to participate.
     * Requires Firestore to be up to date.
     *
     * @param count the invitation count
     */
    public void setInvitationCount(int count) {
        this.invitationCount = count;
    }

    /**
     * Returns the number of waitlisted users who have been invited to participate.
     *
     * @return the invitation count
     */
    public int getInvitationCount() {
        return invitationCount;
    }

    /**
     * Returns the epoch time in milliseconds when the event itself takes place.
     *
     * @return the event date in ms, or {@code null} if not set
     */
    public Long getEventDateMs() {
        return eventDateMs;
    }

    /**
     * Sets the epoch time in milliseconds when the event takes place.
     *
     * @param eventDateMs the event date in ms
     */
    public void setEventDateMs(Long eventDateMs) {
        this.eventDateMs = eventDateMs;
    }

    /**
     * Returns the epoch time in milliseconds when the lottery registration window opens.
     *
     * @return the registration start time in ms, or {@code null} if not set
     */
    public Long getRegStartMs() {
        return regStartMs;
    }

    /**
     * Sets the epoch time in milliseconds when the lottery registration window opens.
     *
     * @param regStartMs the registration start time in ms
     */
    public void setRegStartMs(Long regStartMs) {
        this.regStartMs = regStartMs;
    }

    /**
     * Returns the epoch time in milliseconds when the lottery registration window closes.
     *
     * @return the registration end time in ms, or {@code null} if not set
     */
    public Long getRegEndMs() {
        return regEndMs;
    }

    /**
     * Sets the epoch time in milliseconds when the lottery registration window closes.
     *
     * @param regEndMs the registration end time in ms
     */
    public void setRegEndMs(Long regEndMs) {
        this.regEndMs = regEndMs;
    }

    /**
     * Returns the event title.
     *
     * @return the title string, or {@code null} if not set
     */
    public String getTitle() { return title; }

    /**
     * Sets the event title.
     *
     * @param title the title string
     */
    public void setTitle(String title) { this.title = title; }

    /**
     * Returns the event description.
     *
     * @return the description string, or {@code null} if not set
     */
    public String getDescription() { return description; }

    /**
     * Sets the event description.
     *
     * @param description the description string
     */
    public void setDescription(String description) { this.description = description; }

    /**
     * Returns the eligibility/criteria guidelines for this event.
     *
     * @return the criteria guidelines string, or {@code null} if not set
     */
    public String getCriteriaGuidelines() { return criteriaGuidelines; }

    /**
     * Sets the eligibility/criteria guidelines for this event.
     *
     * @param criteriaGuidelines the criteria guidelines string
     */
    public void setCriteriaGuidelines(String criteriaGuidelines) { this.criteriaGuidelines = criteriaGuidelines; }

    /**
     * Returns the tag (category label) for this event.
     *
     * @return the tag string, or {@code null} if not set
     */
    public String getTag() { return tag; }

    /**
     * Sets the tag (category label) for this event.
     *
     * @param tag the tag string
     */
    public void setTag(String tag) { this.tag = tag; }

    /**
     * Returns the geographic address and optional geo-constraint for this event.
     *
     * @return the {@link EventAddress}, or {@code null} if not set
     */
    public EventAddress getAddress() {
        return address;
    }

    /**
     * Sets the geographic address and optional geo-constraint for this event.
     *
     * @param address the {@link EventAddress}
     */
    public void setAddress(EventAddress address) {
        this.address = address;
    }

    /**
     * Returns {@code true} if this event is private (invitation-only).
     *
     * @return {@code true} if the event is private
     */
    public boolean isPrivateEvent() { return privateEvent; }

    /**
     * Sets whether this event is private (invitation-only).
     *
     * @param privateEvent {@code true} to make the event private
     */
    public void setPrivateEvent(boolean privateEvent) { this.privateEvent = privateEvent; }

    /**
     * Returns the URL of the event poster image.
     *
     * @return the poster URL, or {@code null} if not set
     * @deprecated Poster URL functionality is no longer in active use.
     */
    @Deprecated
    public String getPosterUrl() { return posterUrl; }

    /**
     * Sets the URL of the event poster image.
     *
     * @param posterUrl the poster URL
     * @deprecated Poster URL functionality is no longer in active use.
     */
    @Deprecated
    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }

    /**
     * Returns the pre-computed keyword list used for full-text search.
     * Populated by {@link #generateKeywords()}.
     *
     * @return the keyword list, or {@code null} if not yet generated
     */
    public java.util.List<String> getKeywords() { return keywords; }

    /**
     * Sets the keyword list directly (used by Firestore deserialization).
     *
     * @param keywords the keyword list
     */
    public void setKeywords(java.util.List<String> keywords) { this.keywords = keywords; }

    /**
     * Generates a list of search keywords from the event title.
     * Each word in the title contributes all of its prefixes to the keyword list,
     * enabling prefix-based search queries in Firestore.
     * Call this before saving a new or updated Event to Firestore.
     */
    public void generateKeywords() {
        this.keywords = new java.util.ArrayList<>();
        if (title != null) {
            addKeywordsFromText(title);
        }
    }

    private void addKeywordsFromText(String text) {
        String[] words = text.toLowerCase().split("\\s+");
        for (String word : words) {
            for (int i = 1; i <= word.length(); i++) {
                String prefix = word.substring(0, i);
                if (!keywords.contains(prefix)) {
                    keywords.add(prefix);
                }
            }
        }
    }
}
