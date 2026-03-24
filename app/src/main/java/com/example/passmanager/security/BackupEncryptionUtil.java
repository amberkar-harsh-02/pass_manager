package com.example.passmanager.security;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public class BackupEncryptionUtil {

    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 12;
    private static final int ITERATION_COUNT = 100000; // PBKDF2 Iterations (High security, prevents brute force)
    private static final int KEY_LENGTH = 256;

    /**
     * Encrypts the raw JSON data of the Vault using a user-provided password.
     * Packages the Salt, IV, and CipherText into a single byte array for easy file saving.
     */
    public static byte[] encryptBackup(String plainJson, String password) throws Exception {
        SecureRandom random = new SecureRandom();

        // 1. Generate a random Salt
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);

        // 2. Generate a random Initialization Vector (IV) for GCM
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);

        // 3. Stretch the password into a 256-bit AES Key using PBKDF2
        SecretKey secretKey = deriveKey(password, salt);

        // 4. Encrypt the data
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
        byte[] cipherText = cipher.doFinal(plainJson.getBytes("UTF-8"));

        // 5. Package everything together: [SALT] + [IV] + [CIPHERTEXT]
        ByteBuffer byteBuffer = ByteBuffer.allocate(salt.length + iv.length + cipherText.length);
        byteBuffer.put(salt);
        byteBuffer.put(iv);
        byteBuffer.put(cipherText);

        return byteBuffer.array();
    }

    /**
     * Decrypts the `.ledger` backup file byte array back into the raw JSON string.
     */
    public static String decryptBackup(byte[] encryptedData, String password) throws Exception {
        ByteBuffer byteBuffer = ByteBuffer.wrap(encryptedData);

        // 1. Extract the Salt
        byte[] salt = new byte[SALT_LENGTH];
        byteBuffer.get(salt);

        // 2. Extract the IV
        byte[] iv = new byte[IV_LENGTH];
        byteBuffer.get(iv);

        // 3. Extract the actual Encrypted Data
        byte[] cipherText = new byte[byteBuffer.remaining()];
        byteBuffer.get(cipherText);

        // 4. Rebuild the exact same AES key using the extracted Salt and user password
        SecretKey secretKey = deriveKey(password, salt);

        // 5. Decrypt
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        GCMParameterSpec spec = new GCMParameterSpec(128, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);

        byte[] plainTextBytes = cipher.doFinal(cipherText);
        return new String(plainTextBytes, "UTF-8");
    }

    /**
     * The PBKDF2 Key Derivation Function.
     */
    private static SecretKey deriveKey(String password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }
}