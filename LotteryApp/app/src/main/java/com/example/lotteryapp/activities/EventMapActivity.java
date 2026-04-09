package com.example.lotteryapp.activities;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.lotteryapp.R;
import com.example.lotteryapp.models.Event;
import com.example.lotteryapp.models.EventAddress;
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
import org.osmdroid.views.overlay.Polygon;

import java.util.ArrayList;
import java.util.List;

/**
 * Activity for displaying a map of all waitlisted entrants
 *      Launched from EventDetailsActivity via Event Map button when User is Organizer of the event.
 *      Uses osmdroid for rendering the map and pinpoints - is free.
 */
public class EventMapActivity extends AppCompatActivity {
    private static final int RADIUS_STROKE_COLOR = 0xFF1565C0;
    private static final int RADIUS_FILL_COLOR = 0x401E88E5;

    private MapView mapView;
    private MaterialButton btnClose;
    private EventStorage eventStorage;
    private UserStorage userStorage;
    private String eventId;
    private Event currentEvent;

    /**
     * Inflates layout and binds UI components, retrieves the event ID passed via Intent, and sets up the event map.
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Required for osmdroid configuration (user agent must be set)
        Configuration.getInstance().setUserAgentValue(getPackageName());

        setContentView(R.layout.activity_event_map);

        mapView = findViewById(R.id.event_map_view);
        btnClose = findViewById(R.id.btn_close_event_map);

        // close button finishes the activity
        btnClose.setOnClickListener(v -> finish());

        // Retrieve event ID passed from previous activity
        eventId = getIntent().getStringExtra(EventDetailsActivity.EXTRA_EVENT_ID);
        if (eventId == null || eventId.trim().isEmpty()) {
            Toast.makeText(this, "Missing event ID", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        eventStorage = ServiceLocator.getEventStorage();
        userStorage = ServiceLocator.getUserStorage();

        setupMap();
        loadMapData();
    }
    /**
     * Configures initial map settings such as tile source, zoom level, and enabling multi-touch controls.
     */
    private void setupMap() {
        mapView.setTileSource(TileSourceFactory.MAPNIK);
        mapView.setMultiTouchControls(true);
        mapView.getController().setZoom(10.0);
    }


    /**
     * Requests joined map entries for the event and triggers rendering
     */
    private void loadMapData() {
        eventStorage.getEvent(
                eventId,
                event -> {
                    currentEvent = event;
                    loadJoinedMapMarkers();
                },
                e -> Toast.makeText(this, "Failed to load event map", Toast.LENGTH_SHORT).show()
        );
    }

    /**
     * Fetches joined entrant location data for the current event and renders it on the map.
     */
    private void loadJoinedMapMarkers() {
        eventStorage.getEventJoinedMapEntries(
                eventId,
                this::renderMarkers,
                e -> Toast.makeText(this, "Failed to load map entries", Toast.LENGTH_SHORT).show()
        );
    }

    /**
     * Adds the event location marker to the map and optionally renders a radius overlay
     * if the event has a geo constraint.
     */
    private void addEventMarkerAndRadius(List<GeoPoint> boundsPoints) {
        if (currentEvent == null) {
            return;
        }

        EventAddress eventAddress = currentEvent.getAddress();

        // Ensure event has valid coordinates
        if (eventAddress == null
                || eventAddress.getLatitude() == null
                || eventAddress.getLongitude() == null) {
            return;
        }

        GeoPoint eventPoint = new GeoPoint(
                eventAddress.getLatitude(),
                eventAddress.getLongitude()
        );

        // Include event point in bounds calculation
        boundsPoints.add(eventPoint);

       // only the radius depends on the geo flag
        if (currentEvent.hasGeoConstraint()
                && eventAddress.getRadiusKm() != null
                && eventAddress.getRadiusKm() > 0) {

            Polygon radiusCircle = new Polygon();

            // Create circular polygon centered at event location
            radiusCircle.setPoints(
                    Polygon.pointsAsCircle(eventPoint, eventAddress.getRadiusKm() * 1000.0)
            );
            radiusCircle.setTitle("Event Waitlist Radius");
            radiusCircle.setStrokeColor(RADIUS_STROKE_COLOR);
            radiusCircle.setFillColor(RADIUS_FILL_COLOR);
            radiusCircle.setStrokeWidth(4f);


            //Add the radius first so it draws underneath the markers.
            mapView.getOverlays().add(radiusCircle);

            addRadiusExtents(boundsPoints, eventPoint, eventAddress.getRadiusKm());
        }


        // Always add the event marker once the event has a valid address regardless of whether a geo-constraint exists.
        Marker eventMarker = new Marker(mapView);
        eventMarker.setPosition(eventPoint);
        eventMarker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
        eventMarker.setIcon(createEventMarkerIcon());

        String eventTitle = currentEvent.getTitle() != null && !currentEvent.getTitle().trim().isEmpty()
                ? currentEvent.getTitle()
                : "Event";
        eventMarker.setTitle(eventTitle);
        //eventMarker.setSnippet("Event Location");

        // Build marker description
        StringBuilder details = new StringBuilder();
        if (eventAddress.getLocation() != null && !eventAddress.getLocation().trim().isEmpty()) {
            details.append(eventAddress.getLocation());
        } else {
            details.append("No address available");
        }

        // Append geo constraint information
        if (currentEvent.hasGeoConstraint()
                && eventAddress.getRadiusKm() != null
                && eventAddress.getRadiusKm() > 0) {
            details.append("\n Waitlist Radius: ")
                    .append(eventAddress.getRadiusKm())
                    .append(" km");
        }

        eventMarker.setSubDescription(details.toString());

        // Clicking marker centers map and shows info window
        eventMarker.setOnMarkerClickListener((marker, map) -> {
            marker.showInfoWindow();
            map.getController().animateTo(marker.getPosition());
            return true;
        });

        // Add the event marker after the circle so it stays visually above the radius.
        mapView.getOverlays().add(eventMarker);
    }

    /**
     * This method approximates the geographic bounds of a circular radius with respect to earth's curvature
     */
    private void addRadiusExtents(List<GeoPoint> boundsPoints, GeoPoint center, int radiusKm) {
        // Approximate latitude delta (degrees per km)
        double latDelta = radiusKm / 111.32d;

        // Adjust longitude delta based on latitude (to account for Earth's curvature)
        double lngDivisor = 111.32d * Math.cos(Math.toRadians(center.getLatitude()));
        double lngDelta = lngDivisor == 0 ? 0 : radiusKm / lngDivisor;

        // Add bounding points (N, S, E, W)
        boundsPoints.add(new GeoPoint(center.getLatitude() + latDelta, center.getLongitude()));
        boundsPoints.add(new GeoPoint(center.getLatitude() - latDelta, center.getLongitude()));
        boundsPoints.add(new GeoPoint(center.getLatitude(), center.getLongitude() + lngDelta));
        boundsPoints.add(new GeoPoint(center.getLatitude(), center.getLongitude() - lngDelta));
    }


    /**
     * Adds a marker to the map for a single joined entrant.
     */
    private void addJoinedMapMarker(EventJoinedMap entry, GeoPoint point) {
        UserAddress joinedFrom = entry.getJoinedFrom();
        String uid = joinedFrom.getUid();
        final String locationText = joinedFrom.getLocation();

        // no uid -> cannot resolve user -> show fallback marker
        if (uid == null || uid.trim().isEmpty()) {
            Marker marker = new Marker(mapView);
            marker.setPosition(point);
            marker.setTitle("Unknown entrant");
            marker.setSubDescription(locationText);
            marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
            marker.setIcon(createDefaultMarkerIcon());
            marker.setOnMarkerClickListener((clickedMarker, map) -> {
                clickedMarker.showInfoWindow();
                return true;
            });
            mapView.getOverlays().add(marker);
            mapView.invalidate();
            return;
        }

        // attempt to get the user profile
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
                    marker.setIcon(createDefaultMarkerIcon());
                    marker.setOnMarkerClickListener((clickedMarker, map) -> {
                        clickedMarker.showInfoWindow();
                        return true;
                    });
                    mapView.getOverlays().add(marker);
                    mapView.invalidate();
                },
                e -> {
                    Marker marker = new Marker(mapView);
                    marker.setPosition(point);
                    marker.setTitle("Unknown Entrant");
                    marker.setSubDescription(locationText);
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM);
                    marker.setIcon(createDefaultMarkerIcon());
                    marker.setOnMarkerClickListener((clickedMarker, map) -> {
                        clickedMarker.showInfoWindow();
                        return true;
                    });
                    mapView.getOverlays().add(marker);
                    mapView.invalidate();
                }
        );
    }

    /**
     * helper for creating the marker for the Event
     */
    private Drawable createEventMarkerIcon() {
        Drawable base = ContextCompat.getDrawable(this, org.osmdroid.library.R.drawable.marker_default);
        if (base == null) {
            return null;
        }

        Drawable.ConstantState state = base.getConstantState();
        Drawable markerDrawable = state != null
                ? state.newDrawable().mutate()
                : base.mutate();

        int eventColor = ContextCompat.getColor(this, R.color.event_pin);
        return recolorMarkerPreserveWhiteAndOutline(markerDrawable, eventColor);
    }

    /**
     * helper for creating the default marker (Entrant)
     */
    private Drawable createDefaultMarkerIcon() {
        Drawable base = ContextCompat.getDrawable(this, org.osmdroid.library.R.drawable.marker_default);
        if (base == null) {
            return null;
        }

        Drawable.ConstantState state = base.getConstantState();
        Drawable marker = state != null
                ? state.newDrawable().mutate()
                : base.mutate();

        marker.clearColorFilter();
        return marker;
    }

    /**
     * Renders all valid markers onto the map and adjusts the camera view.
     *      filters out invalid entries (null or missing coordinates)
     *      adds markers for valid entries
     *      centers map for a single marker
     *      fits bounding box for multiple markers
     */
    private void renderMarkers(List<EventJoinedMap> entries) {
        mapView.getOverlays().clear();

        List<GeoPoint> points = new ArrayList<>();

        addEventMarkerAndRadius(points);

        if (entries != null) {
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
        }

        if (points.isEmpty()) {
            Toast.makeText(this, "No valid event or joined locations found", Toast.LENGTH_SHORT).show();
            mapView.invalidate();
            return;
        }

        centerMap(points);
        mapView.invalidate();
    }

    /**
     * render the map as being centered about a point (lat, long)
     */
    private void centerMap(List<GeoPoint> points) {
        EventAddress eventAddress = currentEvent != null ? currentEvent.getAddress() : null;

        if (eventAddress != null
                && eventAddress.getLatitude() != null
                && eventAddress.getLongitude() != null) {
            GeoPoint eventPoint = new GeoPoint(eventAddress.getLatitude(), eventAddress.getLongitude());
            mapView.getController().setCenter(eventPoint);
        }

        if (points.size() == 1) {
            mapView.getController().setCenter(points.get(0));
            mapView.getController().setZoom(12.0);
            return;
        }

        BoundingBox box = BoundingBox.fromGeoPoints(points);

        if (mapView.getWidth() > 0 && mapView.getHeight() > 0) {
            mapView.zoomToBoundingBox(box, true, 100);
        } else {
            mapView.getController().setCenter(box.getCenterWithDateLine());
            mapView.getController().setZoom(5.0);
        }
    }
    /**
     * Recolors a marker drawable while preserving white highlights and dark outlines.
     */
    private Drawable recolorMarkerPreserveWhiteAndOutline(Drawable drawable, int targetColor) {
        Bitmap source = drawableToBitmap(drawable);

        // Create a mutable copy so we can safely modify pixels
        Bitmap recolored = source.copy(Bitmap.Config.ARGB_8888, true);

        int width = recolored.getWidth();
        int height = recolored.getHeight();

        // Extract HSV representation of the target color
        float[] targetHsv = new float[3];
        Color.colorToHSV(targetColor, targetHsv);

        // Iterate over every pixel in the bitmap
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = recolored.getPixel(x, y);
                int alpha = Color.alpha(pixel);

                if (alpha == 0) { // skip transparent pixels
                    continue;
                }

                int r = Color.red(pixel);
                int g = Color.green(pixel);
                int b = Color.blue(pixel);

                // preserve the white click/hand icon and bright highlights.
                if (isNearWhite(r, g, b)) {
                    continue;
                }
                // preserve dark outline/border details.
                if (isNearBlack(r, g, b)) {
                    continue;
                }

                // recolor only the body of the pin
                float[] hsv = new float[3];
                Color.colorToHSV(pixel, hsv);

                // Replace hue + saturation with target color
                // Keep value (brightness) to preserve shading
                hsv[0] = targetHsv[0];
                hsv[1] = targetHsv[1];

                int newPixel = Color.HSVToColor(alpha, hsv);
                recolored.setPixel(x, y, newPixel);
            }
        }
        // Wrap modified bitmap back into a drawable
        return new BitmapDrawable(getResources(), recolored);
    }

    /**
     * marker color setter helper
     *      takes in a drawable object as an argument and returns a bitmap for that object
     *
     */
    private Bitmap drawableToBitmap(Drawable drawable) {
        int width = Math.max(1, drawable.getIntrinsicWidth());
        int height = Math.max(1, drawable.getIntrinsicHeight());

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    // marker color setter helper
    private boolean isNearWhite(int r, int g, int b) {
        return r >= 235 && g >= 235 && b >= 235;
    }

    // marker color setter helper
    private boolean isNearBlack(int r, int g, int b) {
        return r <= 45 && g <= 45 && b <= 45;
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