package com.example.lotteryapp.models;

/** Model class for a User
 *  - Metadata only
 *  - primary key: user Id (device Id)
 *  - A user can be an Entrant, Organizer and /or Admin
 *  - A user cannot be an Entrant and an Organizer of the same event
 *      Constructor only needs device_id (from AuthService)
 *  - Only consists of getters and setters
 */

public class User {
    private final String uuid;
    public String name;
    public String email;
    public String phoneNumber;
    public String location;
    private boolean isAdmin;

    private boolean isAnon;

    public boolean isAnon() {
        return isAnon;
    }

    public void setAnon(boolean anon) {
        isAnon = anon;
    }

    private String profilePicUrl;

    public User(String deviceID) {
        if (deviceID == null || deviceID.trim().isEmpty()) {
            throw new IllegalArgumentException("userId required");
        }

        this.uuid = deviceID;
        if (isAnon) {
            // this.name = createAnonymousName();
        }
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
