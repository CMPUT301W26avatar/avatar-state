package com.example.lotteryapp.models;

import android.graphics.Bitmap;
import android.util.Pair;

import com.example.lotteryapp.R;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.io.ByteArrayOutputStream;

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
        return String.format("%s?event=%s",
                "https://avatar-state-api.web.app/event", EVENT_ID);
    }

    public Bitmap getBitmap() {
        BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
        try {
            return barcodeEncoder.encodeBitmap(getURI(), BarcodeFormat.QR_CODE, WIDTH, HEIGHT);
        } catch (WriterException e) {
            // TODO: Change to proper error handling
            throw new RuntimeException(e); // Failed to create QRCode Bitmap
        }
    }

    public byte[] getPngData() {
        Bitmap bitmap = getBitmap();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);
        return bos.toByteArray();
    }

    public Pair<Integer, Integer> getDimensions() {
        return new Pair<>(WIDTH, HEIGHT);
    }

}
