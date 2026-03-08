package com.example.lotteryapp;

import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseAuth;

/**
 * Service locator with test overrides.
 *      Application code should fetch services from here (Storage, Service)
 *      - prevents the calling of FirebaseFirestore.getInstance() and FirebaseAuth.getInstance()
 *
 * Tests can override storages/providers so Firebase is never initialized.
 */
public final class ServiceLocator {

    // ---- Overrides (tests) ----
    private static @Nullable FirebaseService overrideFirebaseService = null;

    private static @Nullable EventStorage overrideEventStorage = null;
    private static @Nullable UserStorage overrideUserStorage = null;
    private static @Nullable EventPoolStorage overrideEventPoolStorage = null;

    private static @Nullable UserIdProvider overrideUserIdProvider = null;

    private ServiceLocator() {}

    // ---- Base Firebase access ----

    public static FirebaseService firebase() {
        if (overrideFirebaseService != null) return overrideFirebaseService;
        return new FirebaseService();
    }

    public static UserIdProvider userIdProvider() {
        if (overrideUserIdProvider != null) return overrideUserIdProvider;

        return () -> {
            FirebaseAuth auth = firebase().getAuth();
            return (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        };
    }

    // FOR TESTS: can get a valid uuid without enforcing FirebaseAuth sign-in
    public static @Nullable String uid() {
        return userIdProvider().getUid();
    }

    // ---- Storage repos ----

    public static EventStorage eventStorage() {
        if (overrideEventStorage != null) return overrideEventStorage;
        return new EventStorage(firebase().getDb());
    }

    public static UserStorage userStorage() {
        if (overrideUserStorage != null) return overrideUserStorage;
        return new UserStorage(firebase().getDb());
    }

    public static EventPoolStorage eventPoolStorage() {
        if (overrideEventPoolStorage != null) return overrideEventPoolStorage;
        return new EventPoolStorage(firebase().getDb());
    }

    // ---- Test-only overrides ----

    public static void setFirebaseServiceForTests(@Nullable FirebaseService svc) {
        overrideFirebaseService = svc;
    }

    public static void setEventStorageForTests(@Nullable EventStorage s) {
        overrideEventStorage = s;
    }

    public static void setUserStorageForTests(@Nullable UserStorage s) {
        overrideUserStorage = s;
    }

    public static void setEventPoolStorageForTests(@Nullable EventPoolStorage s) {
        overrideEventPoolStorage = s;
    }

    public static void setUserIdProviderForTests(@Nullable UserIdProvider p) {
        overrideUserIdProvider = p;
    }

    // Call in @After to avoid leaks across tests
    public static void reset() {
        overrideFirebaseService = null;
        overrideEventStorage = null;
        overrideUserStorage = null;
        overrideEventPoolStorage = null;
        overrideUserIdProvider = null;
    }
}