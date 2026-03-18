package com.example.passmanager;

import java.security.SecureRandom;

public class SecurityUtil {

    // Upgraded to public static so the whole app can share it
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
}