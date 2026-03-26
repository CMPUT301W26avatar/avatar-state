package com.example.lotteryapp.services;

import com.example.lotteryapp.models.QRCode;
import com.journeyapps.barcodescanner.BarcodeEncoder;

public class QRCodeService {
    private final BarcodeEncoder barcodeEncoder;
    public QRCodeService() {
        barcodeEncoder = new BarcodeEncoder();
    }

    public QRCode getQRCode(String eventId) {
        return new QRCode(eventId);
    }

    public QRCode getQRCode(String eventId, int width, int height) {
        return new QRCode(eventId, width, height);
    }
}
