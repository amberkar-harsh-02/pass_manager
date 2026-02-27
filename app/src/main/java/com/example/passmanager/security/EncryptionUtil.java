package com.example.passmanager.security;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Pair;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

public class EncryptionUtil {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "VaultMasterKey";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_LENGTH = 128;

    private static SecretKey getSecretKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance(ANDROID_KEYSTORE);
        keyStore.load(null);

        if (keyStore.containsAlias(KEY_ALIAS)) {
            KeyStore.SecretKeyEntry entry = (KeyStore.SecretKeyEntry) keyStore.getEntry(KEY_ALIAS, null);
            return entry.getSecretKey();
        }

        KeyGenerator keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        KeyGenParameterSpec keyGenSpec = new KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build();

        keyGenerator.init(keyGenSpec);
        return keyGenerator.generateKey();
    }

    public static Pair<String, String> encryptPassword(String plainText) throws Exception {
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());

        byte[] iv = cipher.getIV();
        byte[] cipherText = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

        return new Pair<>(
                Base64.encodeToString(cipherText, Base64.NO_WRAP),
                Base64.encodeToString(iv, Base64.NO_WRAP)
        );
    }

    public static String decryptPassword(String base64CipherText, String base64Iv) throws Exception {
        byte[] cipherTextBytes = Base64.decode(base64CipherText, Base64.NO_WRAP);
        byte[] ivBytes = Base64.decode(base64Iv, Base64.NO_WRAP);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, ivBytes);
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec);

        byte[] decodedBytes = cipher.doFinal(cipherTextBytes);
        return new String(decodedBytes, StandardCharsets.UTF_8);
    }
}