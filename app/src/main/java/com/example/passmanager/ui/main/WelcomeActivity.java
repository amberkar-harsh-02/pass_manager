package com.example.passmanager.ui.main;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.passmanager.R;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

public class WelcomeActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // --- THE GATEKEEPER ---
        SharedPreferences prefs = getSharedPreferences("VaultSecurityPrefs", MODE_PRIVATE);
        boolean hasSeenWelcome = prefs.getBoolean("HAS_SEEN_WELCOME", false);

        if (hasSeenWelcome) {
            // They already did the welcome screen. Send them straight to the PIN/Main screen!
            goToNextScreen();
            return; // CRITICAL: Stop the Welcome UI from drawing
        }

        // If it IS their first time, draw the Welcome UI
        setContentView(R.layout.activity_welcome);

        TextInputEditText inputName = findViewById(R.id.input_first_name);
        MaterialButton btnContinue = findViewById(R.id.btn_continue_setup);

        btnContinue.setOnClickListener(v -> {
            String name = inputName.getText().toString().trim();

            if (name.isEmpty()) {
                Toast.makeText(this, "Please enter your name.", Toast.LENGTH_SHORT).show();
                return;
            }

            // Save the name and mark the welcome screen as completed
            prefs.edit().putString("USER_FIRST_NAME", name).apply();
            prefs.edit().putBoolean("HAS_SEEN_WELCOME", true).apply();

            // Proceed to the PIN Setup
            goToNextScreen();
        });
    }

    private void goToNextScreen() {
        // Route them to your existing LockScreenActivity to create their PIN
        Intent intent = new Intent(WelcomeActivity.this, LockScreenActivity.class);
        startActivity(intent);
        finish();
    }
}