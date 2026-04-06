package com.example.lotteryapp.models;

/**
 * Model for the physical address and optional geographic constraint of an event.
 * <ul>
 *   <li>The human-readable {@code location} string is used for display purposes.</li>
 *   <li>{@code latitude} and {@code longitude} enable distance calculations
 *       (e.g. geo-fencing, events-near-me).</li>
 *   <li>{@code radiusKm}, when non-null and positive, defines the geo-constraint radius:
 *       only users within this distance from the event location may join the waitlist.</li>
 *   <li>Stored under:
 *       {@code collection("events").document("eventId").collection("geo").document("address")}</li>
 * </ul>
 */
public class EventAddress {
    private String eventId;
    private String location;
    private Double latitude;
    private Double longitude;
    private Integer radiusKm;

    /**
     * Creates a full EventAddress with display location and coordinates.
     * The geo-constraint radius defaults to {@code null} (disabled).
     *
     * @param eventId   the ID of the event this address belongs to
     * @param location  human-readable address string
     * @param latitude  latitude coordinate
     * @param longitude longitude coordinate
     */
    public EventAddress(String eventId, String location, Double latitude, Double longitude) {
        this.eventId = eventId;
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
        this.radiusKm = null;
    }

    /**
     * Creates a minimal EventAddress with only the event ID set.
     * All other fields are left {@code null} and must be populated via setters.
     *
     * @param eventId the ID of the event this address belongs to
     */
    public EventAddress(String eventId) {
        this.eventId = eventId;
    }

    /**
     * Returns the ID of the event this address belongs to.
     *
     * @return the event ID
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Returns the human-readable location string.
     *
     * @return the location string, or {@code null} if not set
     */
    public String getLocation() {
        return location;
    }

    /**
     * Sets the human-readable location string.
     *
     * @param location the location string
     */
    public void setLocation(String location) {
        this.location = location;
    }

    /**
     * Returns the latitude of the event location.
     *
     * @return the latitude, or {@code null} if not set
     */
    public Double getLatitude() {
        return latitude;
    }

    /**
     * Sets the latitude of the event location.
     *
     * @param latitude the latitude value
     */
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    /**
     * Returns the longitude of the event location.
     *
     * @return the longitude, or {@code null} if not set
     */
    public Double getLongitude() {
        return longitude;
    }

    /**
     * Sets the longitude of the event location.
     *
     * @param longitude the longitude value
     */
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    /**
     * Returns the geo-constraint radius in kilometres.
     * When non-null and greater than zero, only users within this radius
     * of the event location are permitted to join the waitlist.
     *
     * @return the radius in km, or {@code null} if geo-constraint is disabled
     */
    public Integer getRadiusKm() {
        return radiusKm;
    }

    /**
     * Sets the geo-constraint radius in kilometres.
     * Pass {@code null} to disable the geo-constraint.
     *
     * @param radiusKm the radius in km, or {@code null} to disable
     */
    public void setRadiusKm(Integer radiusKm) {
        this.radiusKm = radiusKm;
    }
}
