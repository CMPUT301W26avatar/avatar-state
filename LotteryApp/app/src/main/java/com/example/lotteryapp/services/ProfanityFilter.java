package com.example.lotteryapp.services;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Log;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Loads an encrypted profanity dataset from internal app storage, decrypts it using an Android Keystore-backed AES key.
 *      - first checks the full phrase against the banned list
 *      - then checks each token against the banned list for exact matches
 *      - then checks for sequenced bad words, i.e. "...<****><***><*****>..."
 *
 * Data source: https://github.com/LDNOOBW/List-of-Dirty-Naughty-Obscene-and-Otherwise-Bad-Words/blob/master/en
 *
 * If you want to generate a new encrypted profanity file from a plaintext asset,
 *      uncomment the encryption block and helper method, then add a .txt file list_of_bad_words to app/src/main/assets.
 */
public final class ProfanityFilter {
    private static final String TAG = "ProfanityFilter";

    private static final String KEYSTORE_PROVIDER = "AndroidKeyStore";
    private static final String KEY_ALIAS = "profanity_filter_key";

    // private static final String PLAINTEXT_FILE_NAME = "list_of_bad_words.txt";
    private static final String ENCRYPTED_DIR_NAME = "profanity";
    private static final String ENCRYPTED_FILE_NAME = "list_of_bad_words.enc";

    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    // minimum length of a substring to match to a bad word -> avoids matching ass in class
    private static final int SUBSTRING_MATCH_MIN_LENGTH = 4;

    private static final Set<String> bannedWords = new HashSet<>();

    private static boolean initialized = false;

    private ProfanityFilter() { }

    /**
     * Initializes the profanity filter by loading and decrypting the encrypted bad-word dataset
     * from inside the app files
     * - call multiple times init only occurs once
     */
    public static synchronized void init(Context context) {
        if (initialized) {
            return;
        }

        Context appContext = context.getApplicationContext();

        try {
            File encryptedFile = getEncryptedFile(appContext);

            // Uncomment this block if you want to use a new list of bad words.
            /*
            if (!encryptedFile.exists()) {
                Log.d(TAG, "Encrypted profanity file missing. Creating from asset.");
                encryptAssetToInternalStorage(appContext, encryptedFile);
            }
            */

            // Always load from the encrypted runtime copy.
            loadWordsFromEncryptedFile(encryptedFile);

            initialized = true;
            Log.d(TAG, "Profanity filter initialized. Count=" + bannedWords.size());
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize profanity filter", e);
        }
    }

    /**
     * Determines whether the supplied input contains profanity.
     *    - first checks the full phrase against the banned list
     *    - then checks each token against the banned list for exact matches
     *    - then checks for sequenced bad words, i.e. "...<****><***><*****>..."
     */
    public static boolean containsProfanity(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }

        // First pass: check the whole phrase as a normalized string.
        if (containsBannedPhrase(input)) {
            return true;
        }

        // Second pass: exact token matching + concatenated profanity inside each token.
        String[] tokens = input.split("\\s+");
        for (String token : tokens) {
            String normalizedToken = normalizeWord(token);
            if (normalizedToken.isEmpty()) {
                continue;
            }

            if (bannedWords.contains(normalizedToken)) {
                return true;
            }

            if (containsBannedSubstring(normalizedToken)) {
                return true;
            }
        }

        // Third pass: check the fully normalized input for concatenated profanity
        // spanning punctuation or separators.
        String normalizedWhole = normalizePhrase(input);
        return containsBannedSubstring(normalizedWhole);
    }

    /**
     * checks the full phrase (normalized) against the banned list
     */
    private static boolean containsBannedPhrase(String input) {
        if (input == null || input.trim().isEmpty()) {
            return false;
        }

        String normalizedInput = normalizePhrase(input);

        for (String bannedWord : bannedWords) {
            if (bannedWord == null || bannedWord.isEmpty()) {
                continue;
            }

            if (normalizedInput.contains(bannedWord)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Checks whether a normalized token or normalized whole-input string contains any banned word as a substring.
     */
    private static boolean containsBannedSubstring(String normalizedText) {
        if (normalizedText == null || normalizedText.isEmpty()) {
            return false;
        }

        for (String bannedWord : bannedWords) {
            if (bannedWord == null || bannedWord.isEmpty()) {
                continue;
            }

            if (bannedWord.length() < SUBSTRING_MATCH_MIN_LENGTH) {
                continue;
            }

            if (normalizedText.contains(bannedWord)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Normalizes an individual token for word-by-word matching.
     */
    private static String normalizeWord(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * Normalizes an entire phrase for whole input matching.
     */
    private static String normalizePhrase(String text) {
        return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }

    /**
     * Gets the encrypted profanity file location in internal app storage.
     */
    private static File getEncryptedFile(Context context) {
        File dir = new File(context.getFilesDir(), ENCRYPTED_DIR_NAME);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        return new File(dir, ENCRYPTED_FILE_NAME);
    }

    /**
     * Reads and decrypts the encrypted profanity file from internal storage, then loads the plaintext words into memory.
     */
    private static void loadWordsFromEncryptedFile(File encryptedFile)
            throws IOException, GeneralSecurityException {

        // get the AES key from android keystore
        SecretKey key = getOrCreateKey();

        // read the encrypted file into mem as raw bytes
        byte[] encryptedData;
        try (FileInputStream fis = new FileInputStream(encryptedFile)) {
            encryptedData = readAllBytes(fis);
        }

        // file must contain the initialization vector (12 bytes)
        if (encryptedData.length <= GCM_IV_LENGTH_BYTES) {
            throw new GeneralSecurityException("Encrypted profanity file is invalid or too short.");
        }

        // extract init vector; aes-gcm requires a unique init vector to use during encryption
        byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
        byte[] ciphertext = new byte[encryptedData.length - GCM_IV_LENGTH_BYTES];

        // copy init. vector
        System.arraycopy(encryptedData, 0, iv, 0, GCM_IV_LENGTH_BYTES);
        // copy rest of encrypted bytes
        System.arraycopy(encryptedData, GCM_IV_LENGTH_BYTES, ciphertext, 0, ciphertext.length);

        // configure a cipher for decryption
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));

        // load decrypted banned list
        byte[] plaintext = cipher.doFinal(ciphertext);
        loadWordsFromPlaintext(plaintext);
    }

    /**
     * Loads banned entries from decrypted plaintext into memory
     */
    private static void loadWordsFromPlaintext(byte[] plaintext) throws IOException {
        bannedWords.clear();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(plaintext)))) {

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim().toLowerCase(Locale.ROOT);
                if (!line.isEmpty()) {
                    bannedWords.add(normalizePhrase(line));
                }
            }
        }
    }

    /**
     * Gets the existing AES key from Android Keystore, or creates it if it does not yet exist.
     */
    private static SecretKey getOrCreateKey() throws GeneralSecurityException, IOException {
        // load android keystore
        KeyStore keyStore = KeyStore.getInstance(KEYSTORE_PROVIDER);
        keyStore.load(null);

        // try to get existing key
        SecretKey existingKey = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        if (existingKey != null) {
            return existingKey;
        }

        // generate a new AES key and store it in keystore
            // does nothing if there is an existing key
        KeyGenerator keyGenerator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                KEYSTORE_PROVIDER
        );

        // define how the key can be used - aes algorithm in gcm block mode, no padding and 256 bit key size
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build();

        keyGenerator.init(spec); // generate and store
        return keyGenerator.generateKey();
    }

    /**
     * Reads all bytes from the given input stream
     */
    private static byte[] readAllBytes(InputStream inputStream) throws IOException {
        try (InputStream in = inputStream;
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            // temp buffer for chunked reading
            byte[] buffer = new byte[8192];
            int count;

            // until EOF
            while ((count = in.read(buffer)) != -1) {
                out.write(buffer, 0, count);
            }
            // put bytes back together
            return out.toByteArray();
        }
    }

    // Uncomment this method if you want to use a new list of bad words.
    /*
    private static void encryptAssetToInternalStorage(Context context, File encryptedFile)
            throws IOException, GeneralSecurityException {

        SecretKey key = getOrCreateKey();

        byte[] plaintext;
        try (InputStream assetStream = context.getAssets().open(PLAINTEXT_ASSET_NAME)) {
            plaintext = readAllBytes(assetStream);
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key);

        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(plaintext);

        try (FileOutputStream fos = new FileOutputStream(encryptedFile, false)) {
            // Format: [12-byte IV][ciphertext+tag]
            fos.write(iv);
            fos.write(ciphertext);
            fos.flush();
        }

        Log.d(TAG, "Encrypted profanity asset written to " + encryptedFile.getAbsolutePath());
    }
    */
}