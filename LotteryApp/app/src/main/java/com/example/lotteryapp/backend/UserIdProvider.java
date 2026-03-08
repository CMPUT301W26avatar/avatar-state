package com.example.lotteryapp.backend;

import androidx.annotation.Nullable;

public interface UserIdProvider {
    @Nullable String getUid();
}
