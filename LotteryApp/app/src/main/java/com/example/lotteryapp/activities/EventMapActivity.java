package com.example.lotteryapp.activities;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.lotteryapp.R;
import com.example.lotteryapp.models.EventJoinedMap;
import com.example.lotteryapp.models.UserAddress;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.EventStorage;
import com.example.lotteryapp.services.storage.UserStorage;
import com.google.android.material.button.MaterialButton;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.util.ArrayList;
import java.util.List;

public class EventMapActivity extends AppCompatActivity {

    private MapView mapView;
    private MaterialButton btnClose;
    private EventStorage eventStorage;
    private UserStorage userStorage;
    private String eventId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_event_map);

        mapView = findViewById(R.id.event_map_view);
        btnClose = findViewById(R.id.btn_close_event_map);

        btnClose.setOnClickListener(v -> finish());

        eventId = getIntent().getStringExtra(EventDetailsActivity.EXTRA_EVENT_ID);
        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "Missing event ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        eventStorage = ServiceLocator.getEventStorage();
        userStorage = ServiceLocator.getUserStorage();

        setupMap();
        loadJoinedMapMarkers();
    }

    private void setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(10.0);
    }

    private void loadJoinedMapMarkers() {
        eventStorage.getEventJoinedMapEntries(
                eventId,
                this::renderMarkers,
                e -> Toast.makeText(this, "Failed to load map entries", Toast.LENGTH_SHORT).show()
        );
    }

    private void addJoinedMapMarker(EventJoinedMap entry, GeoPoint point) {
        UserAddress joinedFrom = entry.getJoinedFrom();
        String uid = joinedFrom.getUid();

        final String locationText = joinedFrom.getLocation();

        if (uid == null || uid.trim().isEmpty()) {
            Marker marker = new Marker(mapView);
            marker.setPosition(point);
            marker.setTitle("Unknown entrant");
            marker.setSubDescription(locationText);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            mapView.getOverlays().add(marker);
            mapView.invalidate();
            return;
        }

        userStorage.getUserProfile(
                uid,
                user -> {
                    String entrantName = user.getName();
                    if (entrantName == null || entrantName.trim().isEmpty()) {
                        entrantName = "Unknown entrant";
                    }

                    Marker marker = new Marker(mapView);
                    marker.setPosition(point);
                    marker.setTitle(entrantName);
                    marker.setSubDescription(locationText);
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    mapView.getOverlays().add(marker);
                    mapView.invalidate();
                },
                e -> {
                    Marker marker = new Marker(mapView);
                    marker.setPosition(point);
                    marker.setTitle("Unknown entrant");
                    marker.setSubDescription(locationText);
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    mapView.getOverlays().add(marker);
                    mapView.invalidate();
                }
        );
    }

    private void renderMarkers(List<EventJoinedMap> entries) {
        mapView.getOverlays().clear();

        if (entries == null || entries.isEmpty()) {
            Toast.makeText(this, "No joined locations found", Toast.LENGTH_SHORT).show();
            mapView.invalidate();
            return;
        }

        List<GeoPoint> points = new ArrayList<>();

        for (EventJoinedMap entry : entries) {
            if (entry == null || entry.getJoinedFrom() == null) {
                continue;
            }

            UserAddress joinedFrom = entry.getJoinedFrom();
            Double lat = joinedFrom.getLatitude();
            Double lng = joinedFrom.getLongitude();

            if (lat == null || lng == null) {
                continue;
            }

            GeoPoint point = new GeoPoint(lat, lng);
            points.add(point);

            addJoinedMapMarker(entry, point);
        }



        if (points.isEmpty()) {
            Toast.makeText(this, "No valid joined locations found", Toast.LENGTH_SHORT).show();
            mapView.invalidate();
            return;
        }

        if (points.size() == 1) {
            mapView.getController().setCenter(points.get(0));
            mapView.getController().setZoom(12.0);
        } else {
            BoundingBox box = BoundingBox.fromGeoPoints(points);

            if (mapView.getWidth() > 0 && mapView.getHeight() > 0) {
                mapView.zoomToBoundingBox(box, true, 100);
            } else {
                mapView.getController().setCenter(box.getCenterWithDateLine());
                mapView.getController().setZoom(5.0);
            }
        }

        mapView.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mapView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mapView.onPause();
    }
}