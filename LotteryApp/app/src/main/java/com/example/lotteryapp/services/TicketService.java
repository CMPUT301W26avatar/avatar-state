package com.example.lotteryapp.services;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import com.example.lotteryapp.models.Event;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.MultiFormatWriter;
import com.journeyapps.barcodescanner.BarcodeEncoder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Service responsible for generating, previewing, downloading, and opening confirmation ticket PDFs.
 */
public class TicketService {

    /**
     * Opens a dialog-style preview workflow by generating a temporary
     * PDF file in cache and launching a PDF viewer for it.
     */
    public void previewTicketPDF(Context context, Event event, String entrantId) {
        if (!isValidRequest(context, event, entrantId)) {
            return;
        }

        PdfDocument document = buildTicketPdf(event, entrantId);
        File cacheFile = new File(
                context.getCacheDir(),
                "preview_ticket_" + sanitizeFileName(event.getTitle()) + ".pdf"
        );

        try (FileOutputStream outputStream = new FileOutputStream(cacheFile)) {
            document.writeTo(outputStream);

            Uri uri = FileProvider.getUriForFile(
                    context,
                    context.getPackageName() + ".fileprovider",
                    cacheFile
            );

            openPDF(context, uri);
        } catch (IOException e) {
            Toast.makeText(
                    context,
                    "Error previewing ticket: " + e.getMessage(),
                    Toast.LENGTH_SHORT
            ).show();
        } finally {
            document.close();
        }
    }

    /**
     * Generates the ticket PDF and saves it to Downloads
     */
    public void downloadTicketPDF(Context context, Event event, String entrantId) {
        if (!isValidRequest(context, event, entrantId)) {
            return;
        }

        PdfDocument document = buildTicketPdf(event, entrantId);
        String fileName = "Ticket_"
                + sanitizeFileName(event.getTitle())
                + "_"
                + System.currentTimeMillis()
                + ".pdf";

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues contentValues = new ContentValues();
                contentValues.put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName);
                contentValues.put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/pdf");
                contentValues.put(
                        android.provider.MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DOWNLOADS
                );

                Uri uri = context.getContentResolver().insert(
                        android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        contentValues
                );

                if (uri == null) {
                    Toast.makeText(context, "Unable to create ticket file", Toast.LENGTH_SHORT).show();
                    return;
                }

                try (OutputStream outputStream = context.getContentResolver().openOutputStream(uri)) {
                    if (outputStream == null) {
                        Toast.makeText(context, "Unable to open ticket output stream", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    document.writeTo(outputStream);
                    Toast.makeText(context, "Ticket saved to Downloads", Toast.LENGTH_LONG).show();
                }
            } else {
                File downloadsDir = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_DOWNLOADS
                );
                File file = new File(downloadsDir, fileName);

                try (FileOutputStream outputStream = new FileOutputStream(file)) {
                    document.writeTo(outputStream);
                    Toast.makeText(context, "Ticket saved to Downloads", Toast.LENGTH_LONG).show();
                }
            }
        } catch (IOException e) {
            Toast.makeText(
                    context,
                    "Error saving ticket: " + e.getMessage(),
                    Toast.LENGTH_SHORT
            ).show();
        } finally {
            document.close();
        }
    }

    /**
     * Builds the ticket PDF in memory without deciding whether it is previewed or downloaded
     */
    private PdfDocument buildTicketPdf(Event event, String entrantId) {
        PdfDocument document = new PdfDocument();

        PdfDocument.PageInfo pageInfo =
                new PdfDocument.PageInfo.Builder(400, 600, 1).create();
        PdfDocument.Page page = document.startPage(pageInfo);

        Canvas canvas = page.getCanvas();
        Paint paint = new Paint();

        // Header background
        paint.setColor(Color.parseColor("#F5F5F5"));
        canvas.drawRect(0, 0, 400, 120, paint);

        // Title
        paint.setColor(Color.BLACK);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        paint.setTextSize(24);
        canvas.drawText("OFFICIAL TICKET", 40, 60, paint);

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(14);
        canvas.drawText("Scan QR Code at Entrance", 40, 85, paint);

        // Event details
        int y = 160;
        paint.setTextSize(18);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));

        String title = event.getTitle() != null && !event.getTitle().trim().isEmpty()
                ? event.getTitle()
                : "Untitled Event";
        canvas.drawText(title, 40, y, paint);
        y += 35;

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        paint.setTextSize(14);
        paint.setColor(Color.DKGRAY);

        String location = "Venue TBD";
        if (event.getAddress() != null
                && event.getAddress().getLocation() != null
                && !event.getAddress().getLocation().trim().isEmpty()) {
            location = event.getAddress().getLocation();
        }
        canvas.drawText("Location: " + location, 40, y, paint);
        y += 25;

        String dateStr = "Date TBD";
        if (event.getEventDateMs() != null) {
            SimpleDateFormat sdf = new SimpleDateFormat(
                    "EEEE, MMM dd, yyyy 'at' HH:mm",
                    Locale.getDefault()
            );
            dateStr = sdf.format(new Date(event.getEventDateMs()));
        }
        canvas.drawText("Date: " + dateStr, 40, y, paint);
        y += 40;

        // Entrant info
        paint.setColor(Color.BLACK);
        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        canvas.drawText("Attendee ID:", 40, y, paint);

        paint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.NORMAL));
        canvas.drawText(entrantId, 150, y, paint);
        y += 50;

        // QR code
        try {
            String qrContent = event.getEventId() + "|" + entrantId;
            BitMatrix bitMatrix = new MultiFormatWriter().encode(
                    qrContent,
                    BarcodeFormat.QR_CODE,
                    200,
                    200
            );
            Bitmap bitmap = new BarcodeEncoder().createBitmap(bitMatrix);
            canvas.drawBitmap(bitmap, 100, y, paint);
        } catch (WriterException e) {
            // Keep PDF generation resilient even if QR generation fails.
        }

        // Footer
        paint.setTextSize(10);
        paint.setColor(Color.GRAY);
        canvas.drawText(
                "Generated by LotteryApp - Present this PDF on arrival",
                80,
                580,
                paint
        );

        // Border
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2);
        paint.setColor(Color.BLACK);
        canvas.drawRect(10, 10, 390, 590, paint);

        document.finishPage(page);
        return document;
    }

    /**
     * Opens a PDF Uri in an external viewer.
     */
    private void openPDF(Context context, Uri uri) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(uri, "application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        try {
            context.startActivity(Intent.createChooser(intent, "Open Ticket"));
        } catch (Exception e) {
            Toast.makeText(
                    context,
                    "No PDF viewer found. Please install one to view the ticket.",
                    Toast.LENGTH_LONG
            ).show();
        }
    }

    /**
     * Basic validation before ticket work begins.
     */
    private boolean isValidRequest(Context context, Event event, String entrantId) {
        if (context == null) {
            return false;
        }

        if (event == null || event.getEventId() == null || event.getEventId().trim().isEmpty()) {
            Toast.makeText(context, "Missing event", Toast.LENGTH_SHORT).show();
            return false;
        }

        if (entrantId == null || entrantId.trim().isEmpty()) {
            Toast.makeText(context, "Missing entrant", Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    /**
     * Sanitizes event titles for filesystem-safe file names.
     */
    private String sanitizeFileName(String title) {
        if (title == null || title.trim().isEmpty()) {
            return "event";
        }
        return title.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }
}