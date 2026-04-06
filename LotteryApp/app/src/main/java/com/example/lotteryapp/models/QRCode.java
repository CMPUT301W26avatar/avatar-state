package com.example.lotteryapp.models;

import android.graphics.Bitmap;
import android.util.Pair;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.io.ByteArrayOutputStream;

/**
 * Model representing a QR code for an event.
 * Encodes a deep-link URL pointing to the event's detail page so that scanning
 * the code opens the event in the app or browser.
 *
 */
public class QRCode {
    private final String EVENT_ID;
    private final int WIDTH;
    private final int HEIGHT;

    /**
     * Creates a QR code for the given event using the default 400×400 pixel size.
     *
     * @param eventId the unique identifier of the event to encode
     */
    public QRCode(String eventId) {
        EVENT_ID = eventId;
        WIDTH = 400;
        HEIGHT = 400;
    }

    /**
     * Creates a QR code for the given event with custom dimensions.
     *
     * @param eventId the unique identifier of the event to encode
     * @param width   desired width in pixels
     * @param height  desired height in pixels
     */
    public QRCode(String eventId, int width, int height) {
        EVENT_ID = eventId;
        WIDTH = width;
        HEIGHT = height;
    }

    /**
     * Returns the URL that this QR code encodes.
     * The URL links to the event detail page hosted at the app's web endpoint.
     *
     * @return the deep-link URI string for this event
     */
    public String getURI() {
        return String.format("%s?event=%s",
                "https://avatar-state-api.web.app/event", EVENT_ID);
    }

    /**
     * Generates and returns a {@link Bitmap} rendering of this QR code.
     *
     * @return a Bitmap of the QR code at the configured dimensions
     * @throws RuntimeException if the ZXing encoder fails (TODO: replace with proper error handling)
     */
    public Bitmap getBitmap() {
        BarcodeEncoder barcodeEncoder = new BarcodeEncoder();
        try {
            return barcodeEncoder.encodeBitmap(getURI(), BarcodeFormat.QR_CODE, WIDTH, HEIGHT);
        } catch (WriterException e) {
            // TODO: Change to proper error handling
            throw new RuntimeException(e); // Failed to create QRCode Bitmap
        }
    }

    /**
     * Returns this QR code as a compressed PNG byte array, suitable for
     * uploading to Firebase Storage or embedding in a PDF ticket.
     *
     * @return PNG-encoded bytes of the QR code bitmap
     */
    public byte[] getPngData() {
        Bitmap bitmap = getBitmap();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bos);
        return bos.toByteArray();
    }

    /**
     * Returns the pixel dimensions configured for this QR code.
     *
     * @return a {@link Pair} where the first element is width and the second is height
     */
    public Pair<Integer, Integer> getDimensions() {
        return new Pair<>(WIDTH, HEIGHT);
    }
}
