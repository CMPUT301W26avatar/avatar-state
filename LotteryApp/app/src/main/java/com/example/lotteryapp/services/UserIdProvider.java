package com.example.lotteryapp.services;

import androidx.annotation.Nullable;

/** Decoupled device Id provider
 * - User ID provider (device ID) that is not tied to any other application function
 *      - for the purpose of being able to call for a user id/device id in isolation
 */
public interface UserIdProvider {
    @Nullable String getUid();
}
