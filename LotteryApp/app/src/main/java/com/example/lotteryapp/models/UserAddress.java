package com.example.lotteryapp.models;

/**
 * Model for a user's geographic address.
 * <ul>
 *   <li>The human-readable {@code location} string is used for display purposes.</li>
 *   <li>{@code latitude} and {@code longitude} enable distance calculations
 *       (e.g. geo-fencing, events-near-me).</li>
 *   <li>Stored as one of two sub-documents, determined by {@link User.UserAddressMode}:
 *     <ul>
 *       <li>{@code CURRENT}: {@code /users/{userId}/geo/current}</li>
 *       <li>{@code DEFAULT}: {@code /users/{userId}/geo/default}</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public class UserAddress {
    private String uid;
    private String location;
    private Double latitude;
    private Double longitude;

    /**
     * Creates a UserAddress with all fields populated.
     *
     * @param uid       the user's unique identifier (device ID)
     * @param location  human-readable address string
     * @param latitude  latitude coordinate
     * @param longitude longitude coordinate
     */
    public UserAddress(String uid, String location, Double latitude, Double longitude) {
        this.uid = uid;
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    /**
     * Returns the user ID (device ID) that owns this address.
     *
     * @return the user's UID
     */
    public String getUid() {
        return uid;
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
     * Returns the latitude of this address.
     *
     * @return the latitude, or {@code null} if not set
     */
    public Double getLatitude() {
        return latitude;
    }

    /**
     * Sets the latitude of this address.
     *
     * @param latitude the latitude value
     */
    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    /**
     * Returns the longitude of this address.
     *
     * @return the longitude, or {@code null} if not set
     */
    public Double getLongitude() {
        return longitude;
    }

    /**
     * Sets the longitude of this address.
     *
     * @param longitude the longitude value
     */
    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }
}
