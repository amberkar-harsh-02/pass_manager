package com.example.passmanager;

import java.security.SecureRandom;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

public class SecurityUtil {

    public static String generateSecurePassword() {
        // Your exact pool of characters
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+<>?";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(16); // 16 characters long

        for (int i = 0; i < 16; i++) {
            int randomIndex = random.nextInt(chars.length());
            sb.append(chars.charAt(randomIndex));
        }
        return sb.toString();
    }

    // --- 1. THE TOTP GENERATOR ---
    public static String getTOTPCode(String base32Secret) throws Exception {
        // 1. Decode the secret
        byte[] keyBytes = decodeBase32(base32Secret);

        // 2. Get current time in 30-second windows (The TOTP Standard)
        long timeWindow = System.currentTimeMillis() / 1000 / 30;

        // 3. Convert the time window into an 8-byte array
        byte[] timeBytes = ByteBuffer.allocate(8).putLong(timeWindow).array();

        // 4. Hash the time with the secret key using HMAC-SHA1
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(keyBytes, "RawBytes"));
        byte[] hash = mac.doFinal(timeBytes);

        // 5. Dynamic Truncation (The math that extracts a 6-digit code from a 20-byte hash)
        int offset = hash[hash.length - 1] & 0xf;
        int binary =
                ((hash[offset] & 0x7f) << 24) |
                        ((hash[offset + 1] & 0xff) << 16) |
                        ((hash[offset + 2] & 0xff) << 8) |
                        (hash[offset + 3] & 0xff);

        int otp = binary % 1000000; // Modulo 1 million for 6 digits

        // 6. Format as exactly 6 digits (padding with leading zeros if necessary)
        return String.format("%06d", otp);
    }

    // --- 2. THE BASE32 DECODER ---
    private static byte[] decodeBase32(String base32) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        base32 = base32.toUpperCase().replaceAll("[=]", ""); // Clean the string
        byte[] bytes = new byte[base32.length() * 5 / 8];
        int buffer = 0;
        int next = 0;
        int bitsLeft = 0;

        for (char c : base32.toCharArray()) {
            buffer <<= 5;
            buffer |= alphabet.indexOf(c) & 31;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                bitsLeft -= 8;
                bytes[next++] = (byte) (buffer >> bitsLeft);
            }
        }
        return bytes;
    }
}