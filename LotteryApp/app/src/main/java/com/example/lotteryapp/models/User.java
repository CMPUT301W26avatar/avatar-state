package com.example.lotteryapp.models;

/**
 * Model class for a User.
 */
public class User {
    private final String uuid;
    public String name;
    public String email;
    public String phoneNumber;

    private UserAddress userAddress;

    private boolean isAdmin;

    private boolean isAnon;

    private String profilePicUrl;

    private UserAddressMode addressMode;

    /**
     * Decide whether to resolve the user's location from their current GPS position
     * or from their saved default address.
     */
    public enum UserAddressMode {
        /** Grabs current address from the Android device's location provider. */
        CURRENT,
        /** Use the resolved address that the user has saved as their default. */
        DEFAULT
    }

    /**
     * Creates a new User identified by the given device ID.
     * The address mode defaults to {@link UserAddressMode#DEFAULT}.
     *
     * @param deviceID the unique device identifier; must be non-null and non-blank
     * @throws IllegalArgumentException if {@code deviceID} is null or blank
     */
    public User(String deviceID) {
        if (deviceID == null || deviceID.trim().isEmpty()) {
            throw new IllegalArgumentException("userId required");
        }

        this.uuid = deviceID;
        if (isAnon) {
            // this.name = createAnonymousName();
        }
        this.addressMode = UserAddressMode.DEFAULT;
    }

    /**
     * Returns {@code true} if this user is operating anonymously.
     *
     * @return {@code true} if the user is anonymous
     */
    public boolean isAnon() {
        return isAnon;
    }

    /**
     * Sets whether this user is operating anonymously.
     *
     * @param anon {@code true} to mark the user as anonymous
     */
    public void setAnon(boolean anon) {
        isAnon = anon;
    }

    /**
     * Returns the unique device identifier that acts as the primary key for this user.
     *
     * @return the user's UUID
     */
    public String getUUID() {
        return uuid;
    }

    /**
     * Returns the user's display name.
     *
     * @return the display name, or {@code null} if not set
     */
    public String getName() {
        return name;
    }

    /**
     * Sets the user's display name (US 01.02.01 / 01.02.02).
     *
     * @param name the desired display name
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * Returns the user's email address.
     *
     * @return the email address, or {@code null} if not set
     */
    public String getEmail() {
        return email;
    }

    /**
     * Sets the user's email address (US 01.02.01 / 01.02.02).
     *
     * @param email the email address
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Returns the user's phone number.
     *
     * @return the phone number, or {@code null} if not set
     */
    public String getPhoneNumber() {
        return phoneNumber;
    }

    /**
     * Sets the user's phone number (US 01.02.01 / 01.02.02).
     *
     * @param phoneNumber the phone number
     */
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    /**
     * Returns {@code true} if this user has admin privileges.
     *
     * @return {@code true} if the user is an admin
     */
    public boolean isAdmin() {
        return isAdmin;
    }

    /**
     * Grants or revokes admin privileges for this user.
     *
     * @param admin {@code true} to grant admin privileges
     */
    public void setAdmin(boolean admin) {
        isAdmin = admin;
    }

    /**
     * Returns the user's saved address.
     *
     * @return the {@link UserAddress}, or {@code null} if not set
     */
    public UserAddress getUserAddress() {
        return userAddress;
    }

    /**
     * Sets the user's saved address.
     *
     * @param userAddress the address to save
     */
    public void setUserAddress(UserAddress userAddress) {
        this.userAddress = userAddress;
    }

    /**
     * Returns the URL of the user's profile picture.
     *
     * @return the profile picture URL, or {@code null} if not set
     */
    public String getProfilePicUrl() {
        return profilePicUrl;
    }

    /**
     * Sets the URL of the user's profile picture.
     *
     * @param profilePicUrl the profile picture URL
     */
    public void setProfilePicUrl(String profilePicUrl) {
        this.profilePicUrl = profilePicUrl;
    }

    /**
     * Returns the current address resolution mode for this user.
     *
     * @return {@link UserAddressMode#CURRENT} or {@link UserAddressMode#DEFAULT}
     */
    public UserAddressMode getAddressMode() {
        return addressMode;
    }

    /**
     * Sets the address resolution mode for this user.
     *
     * @param addressMode the desired address mode
     */
    public void setAddressMode(UserAddressMode addressMode) {
        this.addressMode = addressMode;
    }
}
