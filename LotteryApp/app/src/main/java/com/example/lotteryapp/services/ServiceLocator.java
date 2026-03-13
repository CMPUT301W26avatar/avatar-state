package com.example.lotteryapp.services;

import androidx.annotation.Nullable;

import com.example.lotteryapp.services.storage.AdminStorage;
import com.example.lotteryapp.services.storage.EventPoolStorage;
import com.example.lotteryapp.services.storage.EventStorage;
import com.example.lotteryapp.services.storage.UserStorage;
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

    private static @Nullable AuthService overrideAuthService = null;
    private static @Nullable UserNameService overrideUserNameService = null;
    private static @Nullable EventStorage overrideEventStorage = null;
    private static @Nullable UserStorage overrideUserStorage = null;
    private static @Nullable EventPoolStorage overrideEventPoolStorage = null;

    private static @Nullable AdminStorage overrideAdminStorage = null;

    private static @Nullable UserIdProvider overrideUserIdProvider = null;

    private ServiceLocator() {}

    // ---- Base Firebase access ----

    public static FirebaseService getFirebase() {
        if (overrideFirebaseService != null) return overrideFirebaseService;
        return new FirebaseService();
    }

    public static UserIdProvider getUIDProvider() {
        if (overrideUserIdProvider != null) return overrideUserIdProvider;

        return () -> {
            FirebaseAuth auth = getFirebase().getAuth();
            return (auth.getCurrentUser() != null) ? auth.getCurrentUser().getUid() : null;
        };
    }
    public static AuthService getAuthService() {
        if (overrideAuthService != null) return overrideAuthService;
        return new AuthService(getFirebase());
    }

    // FOR TESTS: can get a valid uuid without enforcing FirebaseAuth sign-in
    public static @Nullable String uid() {
        return getUIDProvider().getUid();
    }

    // ---- Storage repos ----

    public static EventStorage getEventStorage() {
        if (overrideEventStorage != null) return overrideEventStorage;
        return new EventStorage(getFirebase().getDb());
    }

    public static UserStorage getUserStorage() {
        if (overrideUserStorage != null) return overrideUserStorage;
        return new UserStorage(getFirebase().getDb());
    }

    public static EventPoolStorage getEventPoolStorage() {
        if (overrideEventPoolStorage != null) return overrideEventPoolStorage;
        return new EventPoolStorage(getFirebase().getDb());
    }

    public static AdminStorage getAdminStorage() {
        if (overrideAdminStorage != null) return overrideAdminStorage;
        return new AdminStorage(getFirebase().getDb());
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

    public static void setAdminStorageForTests(@Nullable AdminStorage a) {
        overrideAdminStorage = a;
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