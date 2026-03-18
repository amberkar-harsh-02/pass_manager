package com.example.passmanager.ui.main;

import static com.example.passmanager.SecurityUtil.generateSecurePassword;

import java.security.SecureRandom;
import android.os.Bundle;
import android.util.Pair;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.example.passmanager.R;
import com.example.passmanager.SecurityUtil;
import com.example.passmanager.data.model.Credential;
import com.example.passmanager.security.EncryptionUtil;
import com.example.passmanager.ui.viewmodel.VaultViewModel;

public class AddCredentialActivity extends AppCompatActivity {

    private VaultViewModel vaultViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // SECURITY: Block screenshots and recent-app previews while typing passwords
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_add_credential);

        vaultViewModel = new ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(this.getApplication())).get(VaultViewModel.class);

        EditText editTextTitle = findViewById(R.id.edit_text_title);
        EditText editTextUsername = findViewById(R.id.edit_text_username);
        EditText editTextPassword = findViewById(R.id.edit_text_password);
        Button buttonSave = findViewById(R.id.button_save_credential);


        android.widget.Button buttonGenerate = findViewById(R.id.button_generate_password);

        buttonGenerate.setOnClickListener(v -> {
            // Generate the password and instantly fill the text box
            String newPassword = generateSecurePassword();
            editTextPassword.setText(newPassword);
        });
        buttonSave.setOnClickListener(v -> {
            String title = editTextTitle.getText().toString().trim();
            String username = editTextUsername.getText().toString().trim();
            String password = editTextPassword.getText().toString().trim();

            if (title.isEmpty() || password.isEmpty()) {
                android.widget.Toast.makeText(AddCredentialActivity.this, "Title and Password are required", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                // 1. CALCULATE HEALTH SCORE (Plaintext)
                int healthScore = calculateHealthScore(password);

                // 2. ENCRYPT THE PASSWORD (Using your Pair<String, String> method)
                android.util.Pair<String, String> encryptedData = EncryptionUtil.encryptPassword(password);
                String encryptedPassword = encryptedData.first; // The cipher text
                String ivString = encryptedData.second;         // The IV

                // 3. SAVE TO DATABASE (Now including the healthScore)
                com.example.passmanager.data.model.Credential credential = new com.example.passmanager.data.model.Credential(
                        title, username, encryptedPassword, ivString, healthScore
                );

                vaultViewModel.insert(credential);
                android.widget.Toast.makeText(AddCredentialActivity.this, "Credential Saved Successfully", android.widget.Toast.LENGTH_SHORT).show();
                finish(); // Close the activity

            } catch (Exception e) {
                e.printStackTrace();
                android.widget.Toast.makeText(AddCredentialActivity.this, "Encryption Error!", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
    }
    String generatedPassword = SecurityUtil.generateSecurePassword();
    private int calculateHealthScore(String password) {
        int score = 0;
        if (password == null || password.isEmpty()) return 0; // 0 = Weak

        // Point system based on length
        if (password.length() >= 8) score++;
        if (password.length() >= 12) score++;
        if (password.length() >= 16) score++;

        // Point system based on character variety (Regex matching)
        if (password.matches(".*[a-z].*")) score++; // Has lowercase
        if (password.matches(".*[A-Z].*")) score++; // Has uppercase
        if (password.matches(".*\\d.*")) score++;   // Has digit
        if (password.matches(".*[!@#$%^&*()_+\\-=\\[\\]{};':\"\\\\|,.<>\\/?].*")) score++; // Has symbol

        // Max possible score is 7. Map this to our 4 tiers:
        if (score <= 3) return 0; // Level 0: Weak
        if (score == 4 || score == 5) return 1; // Level 1: Moderate
        if (score == 6) return 2; // Level 2: Strong
        return 3; // Level 3: Very Strong (7 points)
    }
}