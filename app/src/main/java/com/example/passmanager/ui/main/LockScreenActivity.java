package com.example.passmanager.ui.main;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;

import com.example.passmanager.R;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.Executor;

import com.example.passmanager.data.local.VaultDatabase;
import com.example.passmanager.data.model.AuditLog;
import java.util.concurrent.Executors;

public class LockScreenActivity extends AppCompatActivity {

    private StringBuilder currentPin = new StringBuilder();
    private ImageView[] pinDots;
    private TextView textTitle, textSubtitle;
    private SharedPreferences prefs;

    private String currentState = "UNLOCK";
    private String setupFirstPin = "";

    // The 72-Hour Rule (in milliseconds)
    private static final long SEVENTY_TWO_HOURS = 72L * 60 * 60 * 1000;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_lock_screen);

        prefs = getSharedPreferences("VaultSecurityPrefs", MODE_PRIVATE);

        textTitle = findViewById(R.id.text_lock_title);
        textSubtitle = findViewById(R.id.text_lock_subtitle);

        pinDots = new ImageView[]{
                findViewById(R.id.dot_1), findViewById(R.id.dot_2),
                findViewById(R.id.dot_3), findViewById(R.id.dot_4)
        };

        setupKeypad();

        String savedPinHash = prefs.getString("MASTER_PIN_HASH", null);
        if (savedPinHash == null) {
            // First time setup
            currentState = "CREATE_PIN";
            textTitle.setText("Create Master PIN");
            textSubtitle.setText("Set a 4-digit PIN to secure your vault");
        } else {
            // Unlock flow: Evaluate the 72-hour threat model
            long lastPinTime = prefs.getLong("LAST_SUCCESSFUL_PIN", 0);
            boolean isPinRequired = (System.currentTimeMillis() - lastPinTime) > SEVENTY_TWO_HOURS;

            if (isPinRequired) {
                textSubtitle.setText("72 hours since last PIN entry. PIN required.");
                // Hide the fingerprint button to enforce the rule
                findViewById(R.id.btn_fingerprint).setVisibility(View.INVISIBLE);
            } else {
                // Safe to use biometrics! Auto-launch the prompt.
                showBiometricPrompt();
            }
        }
    }

    private void setupKeypad() {
        int[] numberButtons = {R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4, R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9};

        for (int id : numberButtons) {
            findViewById(id).setOnClickListener(v -> {
                if (currentPin.length() < 4) {
                    currentPin.append(((Button) v).getText().toString());
                    updatePinDots();
                    if (currentPin.length() == 4) processPinEntry();
                }
            });
        }

        findViewById(R.id.btn_backspace).setOnClickListener(v -> {
            if (currentPin.length() > 0) {
                currentPin.deleteCharAt(currentPin.length() - 1);
                updatePinDots();
            }
        });

        findViewById(R.id.btn_fingerprint).setOnClickListener(v -> showBiometricPrompt());
    }

    private void updatePinDots() {
        for (int i = 0; i < 4; i++) {
            if (i < currentPin.length()) {
                pinDots[i].setImageResource(android.R.drawable.presence_online);
                pinDots[i].setColorFilter(ContextCompat.getColor(this, R.color.vault_accent));
            } else {
                pinDots[i].setImageResource(android.R.drawable.presence_invisible);
                pinDots[i].setColorFilter(ContextCompat.getColor(this, R.color.vault_text_secondary));
            }
        }
    }

    private void processPinEntry() {
        String enteredPin = currentPin.toString();

        if (currentState.equals("CREATE_PIN")) {
            setupFirstPin = enteredPin;
            currentState = "CONFIRM_PIN";
            textTitle.setText("Confirm Master PIN");
            textSubtitle.setText("Enter your 4-digit PIN again");
            resetPad();

        } else if (currentState.equals("CONFIRM_PIN")) {
            if (enteredPin.equals(setupFirstPin)) {
                // HASH THE PIN BEFORE SAVING!
                String hashedPin = hashPin(enteredPin);
                prefs.edit().putString("MASTER_PIN_HASH", hashedPin).apply();
                Toast.makeText(this, "PIN Secured & Saved!", Toast.LENGTH_SHORT).show();
                unlockVault(true); // True because they successfully used the PIN
            } else {
                Toast.makeText(this, "PINs do not match. Try again.", Toast.LENGTH_SHORT).show();
                currentState = "CREATE_PIN";
                textTitle.setText("Create Master PIN");
                resetPad();
            }

        } else if (currentState.equals("UNLOCK")) {
            String savedPinHash = prefs.getString("MASTER_PIN_HASH", "");
            String enteredHash = hashPin(enteredPin);

            if (enteredHash != null && enteredHash.equals(savedPinHash)) {
                unlockVault(true); // True because they proved they know the PIN
            } else {
                logAccessAttempt("PIN", false);
                Toast.makeText(this, "Incorrect PIN", Toast.LENGTH_SHORT).show();
                resetPad();
            }
        }
    }

    // --- SECURITY & CRYPTOGRAPHY ---

    private String hashPin(String plainPin) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            // Add a static salt to prevent basic rainbow table attacks
            digest.update("LedgerVaultSalt2026".getBytes(StandardCharsets.UTF_8));
            byte[] hash = digest.digest(plainPin.getBytes(StandardCharsets.UTF_8));
            return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void showBiometricPrompt() {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(LockScreenActivity.this,
                executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                logAccessAttempt("BIOMETRIC", true); // <-- NEW: Log Success
                unlockVault(false);
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                logAccessAttempt("BIOMETRIC", false); // <-- NEW: Log Failure
                Toast.makeText(LockScreenActivity.this, "Biometric failed", Toast.LENGTH_SHORT).show();
            }
        });

        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Ledger")
                .setSubtitle("Confirm your biometrics to access your vault")
                .setNegativeButtonText("Use PIN")
                .build();

        biometricPrompt.authenticate(promptInfo);
    }

    private void unlockVault(boolean resetPinTimer) {
        if (resetPinTimer) {
            prefs.edit().putLong("LAST_SUCCESSFUL_PIN", System.currentTimeMillis()).apply();
        }

        Intent intent = new Intent(LockScreenActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }

    private void resetPad() {
        currentPin.setLength(0);
        updatePinDots();
    }
    private void logAccessAttempt(String eventType, boolean isSuccessful) {
        // Run the database insertion on a background thread so the UI doesn't freeze
        Executors.newSingleThreadExecutor().execute(() -> {
            VaultDatabase db = VaultDatabase.getDatabase(getApplicationContext());
            db.auditLogDao().insertLog(new AuditLog(System.currentTimeMillis(), eventType, isSuccessful));
        });
    }
}