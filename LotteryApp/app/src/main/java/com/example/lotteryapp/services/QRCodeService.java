package com.example.lotteryapp.services;

import android.graphics.Bitmap;
import android.util.Pair;

import com.example.lotteryapp.models.QRCode;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.ChecksumException;
import com.google.zxing.FormatException;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.DecoderResult;
import com.journeyapps.barcodescanner.BarcodeEncoder;
import com.google.zxing.qrcode.decoder.Decoder;

import java.util.Base64;

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
