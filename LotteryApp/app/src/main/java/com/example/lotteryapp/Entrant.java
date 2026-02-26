package com.example.lotteryapp;

public final class Entrant {
    private final String uuid;

    public Entrant(String deviceID) {
        if (deviceID == null || deviceID.trim().isEmpty()) {
            throw new IllegalArgumentException("entrantId required");
        }
        this.uuid = deviceID;
    }

    public String getUUID() {
        return uuid;
    }
}
