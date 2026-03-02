package com.example.passmanager.ui.main;
import android.view.View;
import androidx.fragment.app.Fragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import androidx.annotation.NonNull;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import java.util.concurrent.Executor;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.widget.Toast;
import com.example.passmanager.security.EncryptionUtil;
import android.os.Bundle;
import android.view.WindowManager;
import com.example.passmanager.data.model.Credential;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.passmanager.R;
import com.example.passmanager.ui.viewmodel.VaultViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {

    private VaultViewModel vaultViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // SECURITY: Block screenshots, screen recording, and recent-app previews
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_main);

        RecyclerView recyclerView = findViewById(R.id.recyclerView_credentials);
        FloatingActionButton fabAdd = findViewById(R.id.fab_add_password);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);

        // Initialize the Adapter and attach it to the RecyclerView
        final CredentialAdapter adapter = new CredentialAdapter();
        recyclerView.setAdapter(adapter);

        // Initialize ViewModel
        vaultViewModel = new ViewModelProvider(this).get(VaultViewModel.class);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        android.widget.FrameLayout fragmentContainer = findViewById(R.id.fragment_container);

        // We use if/else instead of switch statements to avoid compile errors in modern Android Studio
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == R.id.nav_vault) {
                // 1. Show Vault, Hide Canvas
                fragmentContainer.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                fabAdd.setVisibility(View.VISIBLE);
                return true;

            } else if (itemId == R.id.nav_profile) {
                // 2. Hide Vault, Show Canvas, Load Profile Screen
                recyclerView.setVisibility(View.GONE);
                fabAdd.setVisibility(View.GONE);
                fragmentContainer.setVisibility(View.VISIBLE);

                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new ProfileFragment())
                        .commit();
                return true;

            } else if (itemId == R.id.nav_security) {
                // Placeholder for now
                android.widget.Toast.makeText(MainActivity.this, "Security coming soon", android.widget.Toast.LENGTH_SHORT).show();
                return true;

            } else if (itemId == R.id.nav_about) {
                // Placeholder for now
                android.widget.Toast.makeText(MainActivity.this, "About coming soon", android.widget.Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });
        // OBSERVE THE DATABASE:
        // Any time a password is added, updated, or deleted in the background,
        // this observer triggers and pushes the fresh list to the UI adapter.
        vaultViewModel.getAllCredentials().observe(this, (java.util.List<com.example.passmanager.data.model.Credential> credentials) -> {
            adapter.setCredentials(credentials);
        });

        adapter.setOnItemClickListener(credential -> {

            // Wrap the entire action inside the biometric prompt!
            authenticateUser(() -> {
                try {
                    String decryptedPassword = EncryptionUtil.decryptPassword(
                            credential.getEncryptedPassword(),
                            credential.getEncryptionIv()
                    );

                    new MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle(credential.getTitle())
                            .setMessage("Username: " + credential.getUsername() + "\n\nPassword: " + decryptedPassword)
                            .setPositiveButton("Close", (dialog, which) -> dialog.dismiss())
                            .setNeutralButton("Copy Password", (dialog, which) -> {
                                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                                android.content.ClipData clip = android.content.ClipData.newPlainText("Vault Password", decryptedPassword);
                                clipboard.setPrimaryClip(clip);
                                android.widget.Toast.makeText(MainActivity.this, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show();
                            })
                            .show();

                } catch (Exception e) {
                    e.printStackTrace();
                    android.widget.Toast.makeText(MainActivity.this, "Decryption Failed!", android.widget.Toast.LENGTH_SHORT).show();
                }
            });

        });
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull androidx.recyclerview.widget.RecyclerView recyclerView, @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder, @NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull androidx.recyclerview.widget.RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                com.example.passmanager.data.model.Credential credentialToDelete = adapter.getCredentialAt(position);

                // Instantly bounce the item back into the list while we verify
                adapter.notifyItemChanged(position);

                // Ask for Fingerprint/Face before showing the delete confirmation
                authenticateUser(() -> {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle("Delete Password?")
                            .setMessage("Are you sure you want to delete the credentials for " + credentialToDelete.getTitle() + "?")
                            .setPositiveButton("Delete", (dialog, which) -> {
                                vaultViewModel.delete(credentialToDelete);
                                android.widget.Toast.makeText(MainActivity.this, "Credential Deleted", android.widget.Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                            .setCancelable(false)
                            .show();
                });
            }
        }).attachToRecyclerView(recyclerView);

        // Setup FAB click listener
        fabAdd.setOnClickListener(v -> {
            // Open the screen to add a new password
            android.content.Intent intent = new android.content.Intent(MainActivity.this, AddCredentialActivity.class);
            startActivity(intent);
        });
    }
    private void authenticateUser(Runnable onSuccessAction) {
        Executor executor = ContextCompat.getMainExecutor(this);

        BiometricPrompt biometricPrompt = new BiometricPrompt(MainActivity.this,
                executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                super.onAuthenticationError(errorCode, errString);
                Toast.makeText(getApplicationContext(), "Authentication error: " + errString, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                // If the fingerprint/PIN matches, execute the action!
                onSuccessAction.run();
            }

            @Override
            public void onAuthenticationFailed() {
                super.onAuthenticationFailed();
                Toast.makeText(getApplicationContext(), "Authentication failed", Toast.LENGTH_SHORT).show();
            }
        });

        // Configure what the prompt looks like and what security types are allowed
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Vault")
                .setSubtitle("Verify your identity to access this credential")
                // Allows Fingerprint/Face OR the device PIN/Pattern
                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG | androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();

        biometricPrompt.authenticate(promptInfo);
    }
}