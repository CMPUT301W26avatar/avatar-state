package com.example.lotteryapp;

import java.sql.Timestamp;
import java.util.UUID;

public class Event {
    public String eventId;
    public final String organizerId;
    public EventStatus status;

    private String title;
    /* Event metadata fields here:

    - Criteria / Guidelines
    - geolocational data?
     */
    private String description;
    private String posterUrl; // US 02.04.01 + 02.04.02

    public int eventCapacity;

    public Integer waitlistCapacity;
    public Long eventDateMs; // milliseconds
    public Long regStartMs; // milliseconds
    public Long regEndMs; // milliseconds
    private String location;
    public enum EventStatus {
        OPEN, // reg. open
        CLOSED, // reg. closed
        ENDED, // event finished
    }

    public Event(String organizerId, EventStatus status, int eventCapacity) {
        this.eventId = UUID.randomUUID().toString();
        this.organizerId = organizerId;
        this.status = status;
        this.eventCapacity = eventCapacity;
    }

    public boolean hasWaitlist() {
        return waitlistCapacity != null;
    }

    public boolean isRegistrationOpen() {
        return status == EventStatus.OPEN;
    }

    public String getEventId() {
        return eventId;
    }

    public String getOrganizerId() {
        return organizerId;
    }

    public EventStatus getStatus() {
        return status;
    }

    public void setStatus(EventStatus status) {
        this.status = status;
    }

    public String getTitle() { return title; }

    public void setTitle(String title) { this.title = title; }

    public String getPosterUrl() {
        return posterUrl;
    }

    public void setPosterUrl(String posterUrl) {
        this.posterUrl = posterUrl;
    }

    public int getEventCapacity() {
        return eventCapacity;
    }

    public void setEventCapacity(int eventCapacity) {
        this.eventCapacity = eventCapacity;
    }

    public Integer getWaitlistCapacity() {
        return waitlistCapacity;
    }

    public void setWaitlistCapacity(Integer waitlistCapacity) {
        this.waitlistCapacity = waitlistCapacity;
    }

    public Long getEventDateMs() {
        return eventDateMs;
    }

    public void setEventDateMs(Long eventDateMs) {
        this.eventDateMs = eventDateMs;
    }

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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }
}
