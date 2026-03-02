package com.example.passmanager.ui.main;

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
            String rawPassword = editTextPassword.getText().toString().trim();

            if (title.isEmpty() || username.isEmpty() || rawPassword.isEmpty()) {
                Toast.makeText(this, "Please fill out all fields", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                // 1. Encrypt the password. This returns Pair(EncryptedString, IVString)
                Pair<String, String> encryptedData = EncryptionUtil.encryptPassword(rawPassword);

                // 2. Create the database model
                Credential newCredential = new Credential();
                newCredential.setTitle(title);
                newCredential.setUsername(username);
                newCredential.setEncryptedPassword(encryptedData.first); // Ciphertext
                newCredential.setEncryptionIv(encryptedData.second);     // Crucial IV
                newCredential.setCategory("Uncategorized"); // You can add a category dropdown later

                // 3. Save to Room database via ViewModel
                vaultViewModel.insert(newCredential);

                // 4. Show success message and close this screen
                Toast.makeText(this, "Saved Securely", Toast.LENGTH_SHORT).show();
                finish(); // Returns the user back to MainActivity

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Encryption Failed!", Toast.LENGTH_LONG).show();
            }
        });
    }
    private String generateSecurePassword() {
        // The pool of characters to choose from
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