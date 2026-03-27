package com.example.lotteryapp.activities;

import static com.example.lotteryapp.models.User.UserAddressMode.CURRENT;
import static com.example.lotteryapp.models.User.UserAddressMode.DEFAULT;

import android.Manifest;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.widget.EditText;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;

import com.example.lotteryapp.R;
import com.example.lotteryapp.models.User;
import com.example.lotteryapp.models.UserAddress;
import com.example.lotteryapp.services.ServiceLocator;
import com.example.lotteryapp.services.storage.UserStorage;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Displays and edits a user's privacy details.
 *      Location:
 *        A User can select to target their current location via android device
 *         or, they can input a default address.
 *         The default address input has a loose auto-correct feature, the input is resolved to a nearest known address,
 *             and will alert the User if the inputted address could not be resolved.
 *             - does not prioritize local addresses, i.e.
 *                  "UofA" -> Arizona, "University of A" -> England, "University of Alberta" -> Edmonton
 *                  but you will get "S
 */

public class UserPrivacyActivity extends AppCompatActivity {
    private RadioGroup rgLocationMode;
    private EditText etDefaultLocation;
    private TextView tvResolvedLocation;
    private TextView tvCurrentLocationStatus;

    private Double selectedLatitude;
    private Double selectedLongitude;
    private String selectedResolvedLocation;
    private User.UserAddressMode selectedMode = DEFAULT;

    private LocationManager locationManager;
    private LocationListener activeLocationListener;

    /**
     * Handles runtime permission requests for location access
     * - needs FINE and COARSE location permission access
     * - receives result asynchronously
     */
    private final ActivityResultLauncher<String[]> locationPermissionLauncher =
            registerForActivityResult(
                    new ActivityResultContracts.RequestMultiplePermissions(),
                    result -> {
                        boolean fineGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_FINE_LOCATION));
                        boolean coarseGranted = Boolean.TRUE.equals(result.get(Manifest.permission.ACCESS_COARSE_LOCATION));

                        // We accept either fine OR coarse location
                        // Fine = GPS-level accuracy
                        // Coarse = network-based (less accurate, but faster and less battery)
                        if (fineGranted || coarseGranted) {
                            fetchCurrentLocation();
                        } else {
                            Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show();
                            selectedMode = DEFAULT; // Fall back to DEFAULT mode so user can still input manually

                            setModeDefaultUi();
                        }
                    }
            );

    /**
     * Creates the activity UI and initializes privacy controls
     */
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_user_privacy);

        Toolbar toolbar = findViewById(R.id.toolbar);
        rgLocationMode = findViewById(R.id.rg_location_mode);
        etDefaultLocation = findViewById(R.id.et_default_location);
        tvResolvedLocation = findViewById(R.id.tv_resolved_location);
        tvCurrentLocationStatus = findViewById(R.id.tv_current_location_status);

        toolbar.setNavigationOnClickListener(v -> finish());

        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        rgLocationMode.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rb_use_current_location) {
                selectedMode = CURRENT;
                selectedResolvedLocation = null;
                selectedLatitude = null;
                selectedLongitude = null;
                tvResolvedLocation.setText("");
                setModeCurrentUi();
                fetchWithPermission();
            } else if (checkedId == R.id.rb_use_default_location) {
                selectedMode = DEFAULT;
                selectedResolvedLocation = null;
                selectedLatitude = null;
                selectedLongitude = null;
                tvResolvedLocation.setText("");
                setModeDefaultUi();
            }
        });

        findViewById(R.id.btn_resolve_default_location).setOnClickListener(v -> {
            String addressText = etDefaultLocation.getText().toString().trim();
            if (addressText.isEmpty()) {
                Toast.makeText(this, "Enter a default location first", Toast.LENGTH_SHORT).show();
                return;
            }


            resolveLocationAsync(addressText, new ResolveLocationCallback() {
                @Override
                public void onResolved(@NonNull String resolvedLocation,
                                       @Nullable Double latitude,
                                       @Nullable Double longitude) {
                    selectedResolvedLocation = resolvedLocation;
                    selectedLatitude = latitude;
                    selectedLongitude = longitude;

                    // Show the normalized / resolved address back to the user.
                    etDefaultLocation.setText(resolvedLocation);
                    tvResolvedLocation.setText(
                            "Resolved: " + resolvedLocation
                                    + "\nLat: " + latitude
                                    + "\nLng: " + longitude
                    );
                }

                @Override
                public void onError(@NonNull String message) {
                    Toast.makeText(UserPrivacyActivity.this, message, Toast.LENGTH_SHORT).show();
                }
            });
        });

        findViewById(R.id.btn_save_privacy).setOnClickListener(v -> savePrivacySettings());

        ((android.widget.RadioButton) findViewById(R.id.rb_use_default_location)).setChecked(true);
        setModeDefaultUi();
    }

    /**
     * Cleans up location updates when the activity is destroyed
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopLocationUpdates();
    }

    /**
     * Applies the UI state for current-location mode
     */
    private void setModeCurrentUi() {
        etDefaultLocation.setEnabled(false);
        findViewById(R.id.btn_resolve_default_location).setEnabled(false);
        tvCurrentLocationStatus.setText("Fetching current device location...");
    }

    /**
     * Applies the UI state for default-location mode
     */
    private void setModeDefaultUi() {
        stopLocationUpdates();
        etDefaultLocation.setEnabled(true);
        findViewById(R.id.btn_resolve_default_location).setEnabled(true);
        tvCurrentLocationStatus.setText("Current device location is off.");
    }

    /**
     * Checks location permission and fetches the current device location if allowed
     */
    private void fetchWithPermission() {
        boolean fineGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        boolean coarseGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (fineGranted || coarseGranted) {
            fetchCurrentLocation();
        } else {
            locationPermissionLauncher.launch(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            });
        }
    }

    /**
     * Fetches the current device location
     * - needs location permission to already be granted
     */
    private void fetchCurrentLocation() {
        boolean fineGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        boolean coarseGranted = ContextCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;

        if (!fineGranted && !coarseGranted) {
            Toast.makeText(this, "Location permission not granted", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            Location gpsLast = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
            Location netLast = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);

            // Prefer a last-known location if one is already available.
            Location bestLast = chooseBetterLocation(gpsLast, netLast);
            if (bestLast != null) {
                applyCurrentLocation(bestLast);
                return;
            }

            stopLocationUpdates();

            activeLocationListener = new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location location) {
                    applyCurrentLocation(location);
                    stopLocationUpdates();
                }
            };

            if (fineGranted) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        0L,
                        0f,
                        activeLocationListener,
                        Looper.getMainLooper()
                );
            }

            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    0L,
                    0f,
                    activeLocationListener,
                    Looper.getMainLooper()
            );

        } catch (SecurityException e) {
            Toast.makeText(this, "Unable to access device location", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Failed to fetch current location", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Applies a newly fetched current location to the screen and resolves it to a readable address
     * - needs a non-null android location
     */
    private void applyCurrentLocation(@NonNull Location location) {
        selectedLatitude = location.getLatitude();
        selectedLongitude = location.getLongitude();

        resolveCoordinatesAsync(selectedLatitude, selectedLongitude, new ReverseGeocodeCallback() {
            @Override
            public void onResolved(@NonNull String resolvedLocation) {
                selectedResolvedLocation = resolvedLocation;
                tvCurrentLocationStatus.setText(
                        "Using current location:\n"
                                + resolvedLocation
                                + "\nLat: " + selectedLatitude
                                + "\nLng: " + selectedLongitude
                );
            }

            @Override
            public void onError(@NonNull String message) {
                // If we cannot resolve these coordinates via geocoding, then
                //  we can keep them anyway, because it is lat/long that is used for calculations.

                selectedResolvedLocation = "Lat: " + selectedLatitude + ", Lng: " + selectedLongitude;
                tvCurrentLocationStatus.setText(
                        "Using current coordinates:\n"
                                + selectedResolvedLocation
                );
            }
        });
    }

    /**
     * Returns the better of two locations based on freshness
     * - needs two nullable location candidates
     */
    @Nullable
    private Location chooseBetterLocation(@Nullable Location a, @Nullable Location b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.getTime() >= b.getTime() ? a : b;
    }

    /**
     * Stops active location updates if a listener is currently registered
     */
    private void stopLocationUpdates() {
        if (locationManager != null && activeLocationListener != null) {
            try {
                locationManager.removeUpdates(activeLocationListener);
            } catch (Exception ignored) {
            }
            activeLocationListener = null;
        }
    }

    /**
     * Validates and saves the user's privacy location settings
     */
    private void savePrivacySettings() {
        if (selectedMode == CURRENT) {
            if (selectedLatitude == null || selectedLongitude == null) {
                Toast.makeText(this, "Current location not available yet", Toast.LENGTH_SHORT).show();
                return;
            }
            if (selectedResolvedLocation == null || selectedResolvedLocation.trim().isEmpty()) {
                selectedResolvedLocation = "Lat: " + selectedLatitude + ", Lng: " + selectedLongitude;
            }
        } else {
            String typedLocation = etDefaultLocation.getText().toString().trim();
            if (typedLocation.isEmpty()) {
                Toast.makeText(this, "Enter and resolve a default location", Toast.LENGTH_SHORT).show();
                return;
            }

            if (selectedResolvedLocation == null || selectedLatitude == null || selectedLongitude == null) {
                Toast.makeText(this, "Resolve the default location before saving", Toast.LENGTH_SHORT).show();
                return;
            }
        }

        String uid = ServiceLocator.uid();
        if (uid == null || uid.trim().isEmpty()) {
            Toast.makeText(this, "User not signed in", Toast.LENGTH_SHORT).show();
            return;
        }

        UserStorage ustore = ServiceLocator.getUserStorage();
        UserAddress userAddress = new UserAddress(
                uid,
                selectedResolvedLocation,
                selectedLatitude,
                selectedLongitude
        );

        if (selectedMode == CURRENT) {
            ustore.setCurrentUserAddress(
                    uid,
                    userAddress,
                    unused -> {
                        Toast.makeText(this, "Privacy settings saved", Toast.LENGTH_SHORT).show();
                        finish();
                    },
                    e -> Toast.makeText(this, "Failed to save privacy settings", Toast.LENGTH_SHORT).show()
            );
        } else {
            ustore.setDefaultUserAddress(
                    uid,
                    userAddress,
                    unused -> {
                        Toast.makeText(this, "Privacy settings saved", Toast.LENGTH_SHORT).show();
                        finish();
                    },
                    e -> Toast.makeText(this, "Failed to save privacy settings", Toast.LENGTH_SHORT).show()
            );
        }
    }

    /**
     * Callback for resolving a text address into a displayable address and coordinates.
     */
    private interface ResolveLocationCallback {
        ///  handles a succesful address resolution
        void onResolved(@NonNull String resolvedLocation,
                        @Nullable Double latitude,
                        @Nullable Double longitude);
        ///  handles a failed resolution
        void onError(@NonNull String message);
    }

    /**
     * Callback for resolving a text address into a displayable address and coordinates.
     */
    private interface ReverseGeocodeCallback {
        ///  handles a succesful reverse-geocode
        void onResolved(@NonNull String resolvedLocation);

        ///  handles a failed reverse-geocode
        void onError(@NonNull String message);
    }

    /**
     * Resolves a text address into a displayable address and coordinates
     * - needs a user-entered address string and LocationResolveCallback interface
     */
    private void resolveLocationAsync(
            @NonNull String addressText,
            @NonNull ResolveLocationCallback callback
    ) {
        if (!Geocoder.isPresent()) {
            callback.onError("Geocoder is not available on this device");
            return;
        }

        // background thread runs the geocoder
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> results;

                // Android 13+ exposes the geocoder callback before it is returned
                //  implement a threadlock on the background geocoder so as not to run past the callback
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    final Object lock = new Object();
                    final List<Address>[] holder = new List[]{null};

                    // lock the background thread that the geocode is running on
                    //  so we are not racing ahead of the callback
                    geocoder.getFromLocationName(addressText, 1, addresses -> {
                        synchronized (lock) {
                            holder[0] = addresses;
                            lock.notify();
                        }
                    });
                    // poll for the geocoder callback
                    synchronized (lock) {
                        if (holder[0] == null) {
                            lock.wait(4000);
                        }
                    }
                    // after poll ends, holder[0] is either filled with the callback on success
                    //  or, null on failure
                    results = holder[0];
                } else {
                    // Since our app is 24->36 this will never run
                    results = geocoder.getFromLocationName(addressText, 1);
                }

                // jump back to the main application thread
                runOnUiThread(() -> {
                    if (results == null || results.isEmpty()) {
                        callback.onError("Could not resolve location");
                        return;
                    }

                    //  convert Android's Address object into a display string
                    Address address = results.get(0);
                    String resolvedLocation = buildResolvedAddress(address, addressText);

                    callback.onResolved(
                            resolvedLocation,
                            address.getLatitude(),
                            address.getLongitude()
                    );
                });

            } catch (IOException e) {
                runOnUiThread(() -> callback.onError("Failed to resolve location"));
            } catch (Exception e) {
                runOnUiThread(() -> callback.onError("Unexpected geocoder error"));
            }
        }).start();
    }

    /**
     * Resolves coordinates into a displayable address.
     * - needs lat+long and ReverseGeocodeCallback interface
     */
    private void resolveCoordinatesAsync(
            @NonNull Double latitude,
            @NonNull Double longitude,
            @NonNull ReverseGeocodeCallback callback
    ) {
        if (!Geocoder.isPresent()) {
            callback.onError("Geocoder is not available on this device");
            return;
        }
        /*
        Same idea as in resolveLocationAsync
            - Run the geocoder operations on a background thread
            - Lock the thread to ensure callbacks are received when ready and not before
            - Pop back onto the main thread to call the geocoder callback into a value via lambda
                - Additional build resolved address helper for building the address
         */
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> results;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    final Object lock = new Object();
                    final List<Address>[] holder = new List[]{null};

                    geocoder.getFromLocation(latitude, longitude, 1, addresses -> {
                        synchronized (lock) {
                            holder[0] = addresses;
                            lock.notify();
                        }
                    });

                    synchronized (lock) {
                        if (holder[0] == null) {
                            lock.wait(4000);
                        }
                    }
                    results = holder[0];
                } else {
                    results = geocoder.getFromLocation(latitude, longitude, 1);
                }

                runOnUiThread(() -> {
                    if (results == null || results.isEmpty()) {
                        callback.onError("Could not resolve current coordinates");
                        return;
                    }

                    callback.onResolved(buildResolvedAddress(results.get(0), ""));
                });

            } catch (IOException e) {
                runOnUiThread(() -> callback.onError("Failed to resolve current coordinates"));
            } catch (Exception e) {
                runOnUiThread(() -> callback.onError("Unexpected reverse geocoder error"));
            }
        }).start();
    }

    /**
     * Returns a resolved display string built from an Address.
     * - needs a non-null android Address and a fallback string if no readable fields exist
     */
    @NonNull
    private String buildResolvedAddress(@NonNull Address address, @NonNull String fallback) {
        StringBuilder resolved = new StringBuilder();

        // Depending on precise the geocoder match was,
        //  some fields may be null, or we may get some unneeded fields
        //  build a nice display string from only what matters from the geocoder callback

        if (address.getFeatureName() != null && !address.getFeatureName().isEmpty()) {
            resolved.append(address.getFeatureName());
        }
        if (address.getThoroughfare() != null && !address.getThoroughfare().isEmpty()) {
            if (resolved.length() > 0) resolved.append(", ");
            resolved.append(address.getThoroughfare());
        }
        if (address.getLocality() != null && !address.getLocality().isEmpty()) {
            if (resolved.length() > 0) resolved.append(", ");
            resolved.append(address.getLocality());
        }
        if (address.getAdminArea() != null && !address.getAdminArea().isEmpty()) {
            if (resolved.length() > 0) resolved.append(", ");
            resolved.append(address.getAdminArea());
        }
        if (address.getCountryName() != null && !address.getCountryName().isEmpty()) {
            if (resolved.length() > 0) resolved.append(", ");
            resolved.append(address.getCountryName());
        }

        return resolved.length() > 0 ? resolved.toString() : fallback;
    }
}