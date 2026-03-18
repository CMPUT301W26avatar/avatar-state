package com.example.lotteryapp.models;

/**
 * Model for the Address of an event
 *      - location for displays
 *      - lat/long for calculations (radius, events-near-me)
 *      - collection("events").document("eventId").collection("geo").document("address")
 */
public class EventAddress {
    private String eventId;
    private String location;
    private Double latitude;
    private Double longitude;
    private Integer radiusKm;

    public EventAddress(String eventId, String location, Double latitude, Double longitude) {
        this.eventId = eventId;
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusKm = null;

    }

    public EventAddress(String eventId) {
        this.eventId = eventId;
    }

    public String getEventId() {
        return eventId;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Integer getRadiusKm() {
        return radiusKm;
    }

    public void setRadiusKm(Integer radiusKm) {
        this.radiusKm = radiusKm;
    }
}
