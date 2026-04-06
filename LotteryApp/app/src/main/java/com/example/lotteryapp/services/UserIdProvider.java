package com.example.lotteryapp.services;

import androidx.annotation.Nullable;

/** Decoupled device Id provider
 * - User ID provider (device ID) that is not tied to any other application function
 *      - for the purpose of being able to call for a user id/device id in isolation
 */
public interface UserIdProvider {
    /**
     * Returns the current user's unique identifier (device ID), or {@code null} if
     * no user is authenticated or the ID cannot be resolved.
     *
     * @return the user's UID, or {@code null}
     */
    @Nullable String getUid();
}
