package com.example.passmanager.ui.main;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.passmanager.R;
import com.example.passmanager.data.model.Credential;
import com.example.passmanager.security.EncryptionUtil;
import com.example.passmanager.ui.viewmodel.VaultViewModel;

import java.security.SecureRandom;

public class EditCredentialActivity extends AppCompatActivity {

    private VaultViewModel vaultViewModel;
    private int credentialId;

    // NEW: We need to hold onto the secret so we don't accidentally delete it!
    private String existingTotpSecret = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // We reuse the Add layout because it looks exactly how we want it to!
        setContentView(R.layout.activity_add_credential);

        // Link the UI elements
        EditText editTextTitle = findViewById(R.id.edit_text_title);
        EditText editTextUsername = findViewById(R.id.edit_text_username);
        EditText editTextPassword = findViewById(R.id.edit_text_password);
        Button buttonSave = findViewById(R.id.button_save_credential);
        Button buttonGenerate = findViewById(R.id.button_generate_password);

        // Change the button text since we are editing
        buttonSave.setText("Update Credential");

        vaultViewModel = new ViewModelProvider(this).get(VaultViewModel.class);

        // 1. CATCH THE INTENT DATA
        android.content.Intent intent = getIntent();
        if (intent != null && intent.hasExtra("CREDENTIAL_ID")) {
            credentialId = intent.getIntExtra("CREDENTIAL_ID", -1);
            editTextTitle.setText(intent.getStringExtra("CREDENTIAL_TITLE"));
            editTextUsername.setText(intent.getStringExtra("CREDENTIAL_USERNAME"));

            // Catch the 2FA secret (if it exists) so we can preserve it
            if (intent.hasExtra("CREDENTIAL_TOTP_SECRET")) {
                existingTotpSecret = intent.getStringExtra("CREDENTIAL_TOTP_SECRET");
            }

            // Decrypt the old password so the user can see what they are replacing
            try {
                String encryptedPw = intent.getStringExtra("CREDENTIAL_ENCRYPTED_PW");
                String iv = intent.getStringExtra("CREDENTIAL_IV");
                String decrypted = EncryptionUtil.decryptPassword(encryptedPw, iv);
                editTextPassword.setText(decrypted);
            } catch (Exception e) {
                Toast.makeText(this, "Failed to decrypt old password", Toast.LENGTH_SHORT).show();
            }
        }

        // 2. GENERATE PASSWORD BUTTON LOGIC
        buttonGenerate.setOnClickListener(v -> {
            editTextPassword.setText(generateSecurePassword());
        });

        // 3. UPDATE LOGIC
        buttonSave.setOnClickListener(v -> {
            String updatedTitle = editTextTitle.getText().toString().trim();
            String updatedUsername = editTextUsername.getText().toString().trim();
            String updatedPassword = editTextPassword.getText().toString().trim();

            if (updatedTitle.isEmpty() || updatedPassword.isEmpty()) {
                Toast.makeText(EditCredentialActivity.this, "Title and Password are required", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                // Calculate new health score
                int newHealthScore = calculateHealthScore(updatedPassword);

                // Encrypt the new password
                android.util.Pair<String, String> encryptedData = EncryptionUtil.encryptPassword(updatedPassword);

                // FIX: Build the updated credential using the SAME ID, and pass the existing TotpSecret back in!
                Credential updatedCredential = new Credential(
                        updatedTitle,
                        updatedUsername,
                        encryptedData.first,
                        encryptedData.second,
                        newHealthScore,
                        existingTotpSecret
                );

                updatedCredential.setId(credentialId); // CRITICAL: This tells Room to update, not insert

                vaultViewModel.update(updatedCredential);
                Toast.makeText(EditCredentialActivity.this, "Vulnerability Patched!", Toast.LENGTH_SHORT).show();
                finish();

            } catch (Exception e) {
                Toast.makeText(EditCredentialActivity.this, "Encryption Error!", Toast.LENGTH_SHORT).show();
            }
        });
    }

    // --- CRYPTOGRAPHY & SCORING HELPERS ---
    private String generateSecurePassword() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()-_=+<>?";
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(chars.charAt(random.nextInt(chars.length())));
        }
        return sb.toString();
    }

    private int calculateHealthScore(String password) {
        int score = 0;
        if (password == null || password.isEmpty()) return 0;
        if (password.length() >= 8) score++;
        if (password.length() >= 12) score++;
        if (password.length() >= 16) score++;
        if (password.matches(".*[a-z].*")) score++;
        if (password.matches(".*[A-Z].*")) score++;
        if (password.matches(".*\\d.*")) score++;
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) score++;

        if (score <= 3) return 0;
        if (score == 4 || score == 5) return 1;
        if (score == 6) return 2;
        return 3;
    }
}