package com.example.lotteryapp.services;

import java.util.Random;

/**
 * Generates a random username for the user
 */
public class UserNameService {
    static final String[] VALID_PREFIXES = {
        "Red", "Green", "Blue", "Yellow", "White", "Black", "Grey", "Rainy", "Snowy", "Salty"
    };

    static final String[] VALID_SUFFIXES = {
        "Apple", "Pear", "Mango", "Sunset", "Grape", "Cola", "Rabbit", "Goose", "Duck", "Snorlax"
    };

    static Random rand = new Random();

    /**
     * Generates and returns a random display name by combining one of the preset
     * adjective prefixes with one of the preset noun suffixes and a two-digit number.
     *
     * @return a randomly generated username string (e.g. {@code "RainyDuck73"})
     */
    public static String generateUserName() {
        final String prefix = VALID_PREFIXES[rand.nextInt(VALID_PREFIXES.length)];
        final String suffix = VALID_SUFFIXES[rand.nextInt(VALID_SUFFIXES.length)];
        return prefix + suffix + rand.nextInt(10) + rand.nextInt(10);
    }
}
