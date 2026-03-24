package com.example.lotteryapp.models;

import android.util.Pair;

import com.example.lotteryapp.R;

public class QRCode {
    private final String EVENT_ID;
    private final int WIDTH;
    private final int HEIGHT;

    public QRCode(String eventId) {
        EVENT_ID = eventId;
        WIDTH = 400;
        HEIGHT = 400;
    }

    public QRCode(String eventId, int width, int height) {
        EVENT_ID = eventId;
        WIDTH = width;
        HEIGHT = height;
    }

    public String getURI() {
        return String.format("%s?event=%s", R.string.qr_code_base_url, EVENT_ID);
    }

    public Pair<Integer, Integer> getDimensions() {
        return new Pair<>(WIDTH, HEIGHT);
    }
}
