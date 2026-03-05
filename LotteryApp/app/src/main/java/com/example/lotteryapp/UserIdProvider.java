package com.example.lotteryapp;

import androidx.annotation.Nullable;

public interface UserIdProvider {
    @Nullable String getUid();
}