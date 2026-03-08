package com.example.lotteryapp.models;
public class User {
    private final String uuid;
    public String name;
    public String email;
    public String phoneNumber;
    public String location;
    private boolean isAdmin;
    private String profilePicUrl;

    public User(String deviceID) {
        if (deviceID == null || deviceID.trim().isEmpty()) {
            throw new IllegalArgumentException("userId required");
        }
        this.uuid = deviceID;
    }

    public String getUUID() {
        return uuid;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) { // US 01.02.01 + US 01.02.02
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) { // US 01.02.01 + US 01.02.02
        this.email = email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {// US 01.02.01 + US 01.02.02
        this.phoneNumber = phoneNumber;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public boolean isAdmin() {
        return isAdmin;
    }

    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }

    public String getProfilePicUrl() {
        return profilePicUrl;
    }

    public void setProfilePicUrl(String profilePicUrl) {
        this.profilePicUrl = profilePicUrl;
    }
}
