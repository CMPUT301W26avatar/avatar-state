package com.example.lotteryapp.services;

import com.example.lotteryapp.models.Entrant;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SelectionService {

    private final SecureRandom random;

    public SelectionService() {
        this.random = new SecureRandom();
    }

    /**
     * Randomly selects up to eventCapacity entrants from the waitlist.
     * Returns a new list and does not mutate the caller's list.
     */
    public List<Entrant> selectRandomWaitlistSubset(List<Entrant> waitlistedEntrants, int eventCapacity) {
        List<Entrant> eventPool = new ArrayList<>();

        if (waitlistedEntrants != null) {
            eventPool.addAll(waitlistedEntrants);
        }

        if (eventPool.isEmpty() || eventCapacity <= 0) {
            return new ArrayList<>();
        }

        Collections.shuffle(eventPool, random);

        int actualCount = Math.min(eventCapacity, eventPool.size());
        return new ArrayList<>(eventPool.subList(0, actualCount));
    }
}