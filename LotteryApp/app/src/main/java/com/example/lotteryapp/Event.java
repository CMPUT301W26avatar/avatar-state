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
    private String posterUrl; // US 02.04.01 + 02.04.02

    public int capacity;
    public Integer waitlistCapacity;
    public Timestamp regStart;
    public Timestamp regEnd;
    public Timestamp drawTime;

    public enum EventStatus {
        OPEN, // reg. open
        CLOSED, // reg. closed
        ENDED, // event finished
    }

    public Event(String organizerId, EventStatus status, int capacity) {
        this.eventId = UUID.randomUUID().toString();
        this.organizerId = organizerId;
        this.status = status;
        this.capacity = capacity;
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

    public int getCapacity() {
        return capacity;
    }

    public void setCapacity(int capacity) {
        this.capacity = capacity;
    }

    public Integer getWaitlistCapacity() {
        return waitlistCapacity;
    }

    public void setWaitlistCapacity(Integer waitlistCapacity) {
        this.waitlistCapacity = waitlistCapacity;
    }

    public Timestamp getRegStart() {
        return regStart;
    }

    public void setRegStart(Timestamp regStart) {
        this.regStart = regStart;
    }

    public Timestamp getRegEnd() {
        return regEnd;
    }

    public void setRegEnd(Timestamp regEnd) {
        this.regEnd = regEnd;
    }

    public Timestamp getDrawTime() {
        return drawTime;
    }

    public void setDrawTime(Timestamp drawTime) {
        this.drawTime = drawTime;
    }
}
