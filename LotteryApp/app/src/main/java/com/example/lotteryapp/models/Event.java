package com.example.lotteryapp.models;

import java.util.List;
import java.util.UUID;

/** Model class for an Event
 * - Mostly metadata, any of the Count attributes are updated in real time
 * - primary key: event ID, foreign key: user ID (organizerId)
 * - New Event: Pull Event document from Firebase and set EventID as docID.
 * - Only getters and setters
 *
 * PosterUrl attribute deprecated
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

    public enum EventStatus {
        REG_UPCOMING,
        REG_OPEN, // reg. open, reg. start date passed
        REG_CLOSED, // reg. closed, reg. end date passed
        REG_FULL, // reg. open but exceeding waitlist capacity
        EVENT_OPEN, // event open, set after reg. end date, event still has capacity
        EVENT_CLOSED, // event closed/finished
        EVENT_FULL, // event open, but exceeding event capacity
    }

    public Event() {
        // Required for Firebase toObject()
    }

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

    // pk
    public String getEventId() {
        return eventId;
    }

    // only used when pulling a new Event from firebase and instantiating it as the document Id
    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    // fk
    public String getOrganizerId() {
        return organizerId;
    }

    public List<String> getCoOrganizerIds() {
        return coOrganizerIds;
    }

    public void setCoOrganizerIds(List<String> coOrganizerIds) {
        this.coOrganizerIds = coOrganizerIds;
    }

    public boolean isRegistrationOpen() {
        return this.status == EventStatus.REG_OPEN;
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
    }

    public boolean hasDrawnLottery() {
        return hasDrawnLottery;
    }

    public void setHasDrawnLottery(boolean hasDrawnLottery) {
        this.hasDrawnLottery = hasDrawnLottery;
    }

    public boolean hasGeoConstraint() {
        return hasGeoConstraint;
    }

    public void setHasGeoConstraint(boolean hasGeoConstraint) {
        this.hasGeoConstraint = hasGeoConstraint;
    }

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

    // how many user allowed to participate in the event
    public int getEventCapacity() {
        return eventCapacity;
    }

    public void setEventCapacity(int eventCapacity) {
        this.eventCapacity = eventCapacity;
    }

    // how many users are confirmed (waitlisted>invited>accepted) to be participating in the event
    public int getEnrolledCount() {
        return enrolledCount;
    }

    public void setEnrolledCount(int enrolledCount) {
        this.enrolledCount = enrolledCount;
    }

    // how many users allowed to join the waitlist to participate in the event
    public Integer getWaitlistCapacity() {
        return waitlistCapacity;
    }

    public void setWaitlistCapacity(Integer waitlistCapacity) {
        this.waitlistCapacity = waitlistCapacity;
    }

    // how many users are currently on the waitlist to participate in the event
    //      requires firebase
    public int getWaitlistCount() {
        return waitlistCount;
    }

    public void setWaitlistCount(int waitlistCount) {
        this.waitlistCount = waitlistCount;
    }

    // users on the waitlist who have been selected (invited) to participate in the event
    //      requires firebase
    public void setInvitationCount(int count) {
        this.invitationCount = count;
    }

    public int getInvitationCount() {
        return invitationCount;
    }

    // date of the event
    public Long getEventDateMs() {
        return eventDateMs;
    }

    public void setEventDateMs(Long eventDateMs) {
        this.eventDateMs = eventDateMs;
    }

    // lottery registration window for the event
    public Long getRegStartMs() {
        return regStartMs;
    }

    public void setRegStartMs(Long regStartMs) {
        this.regStartMs = regStartMs;
    }

    public Long getRegEndMs() {
        return regEndMs;
    }

    public void setRegEndMs(Long regEndMs) {
        this.regEndMs = regEndMs;
    }

    // event metadata
    public String getTitle() { return title; }

    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }

    public void setDescription(String description) { this.description = description; }

    public String getCriteriaGuidelines() { return criteriaGuidelines; }

    public void setCriteriaGuidelines(String criteriaGuidelines) { this.criteriaGuidelines = criteriaGuidelines; }

    public String getTag() { return tag; }

    public void setTag(String tag) { this.tag = tag; }

    public EventAddress getAddress() {
        return address;
    }

    public void setAddress(EventAddress address) {
        this.address = address;
    }

    public boolean isPrivateEvent() { return privateEvent; }

    public void setPrivateEvent(boolean privateEvent) { this.privateEvent = privateEvent; }

    public String getPosterUrl() { return posterUrl; }

    public void setPosterUrl(String posterUrl) { this.posterUrl = posterUrl; }

    public java.util.List<String> getKeywords() { return keywords; }

    public void setKeywords(java.util.List<String> keywords) { this.keywords = keywords; }

    /**
     * Generates a list of keywords for searching.
     * Includes all words in the title and description
     * and their prefixes.
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
