package com.example.lotteryapp.models;

/**
 * Model for the Address of a User
 *      - location for displays
 *      - lat/long for calculations (radius, events-near-me)
 *      - User enum UserAddressMode CURRENT or DEFAULT determines:
 *          - collection("users").document("userId")
 *              .collection("geo").document("current")
 *
 *          - collection("users").document("userId")
 *              .collection("geo").document("default")
 */
public class UserAddress {
    private String uid;
    private String location;
    private Double latitude;
    private Double longitude;

    public UserAddress(String uid, String location, Double latitude, Double longitude) {
        this.uid = uid;
        this.location = location;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getUid() {
        return uid;
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
}
