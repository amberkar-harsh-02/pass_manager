package com.example.passmanager.ui.main;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.passmanager.R;
import com.example.passmanager.security.EncryptionUtil;
import com.example.passmanager.ui.viewmodel.VaultViewModel;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class MainActivity extends AppCompatActivity {
    private List<com.example.passmanager.data.model.Credential> allCredentials = new ArrayList<>();
    private VaultViewModel vaultViewModel;
    private CredentialAdapter adapter;
    private EditText searchBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // SECURITY: Block screenshots
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setContentView(R.layout.activity_main);

        // --- 1. LINK ALL UI ELEMENTS TO IDs FIRST ---
        RecyclerView recyclerView = findViewById(R.id.recyclerView_credentials);
        FloatingActionButton fabAdd = findViewById(R.id.fab_add_password);
        searchBar = findViewById(R.id.edit_text_search);
        FrameLayout fragmentContainer = findViewById(R.id.fragment_container);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        // --- 2. SETUP RECYCLERVIEW & ADAPTER ---
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        adapter = new CredentialAdapter();
        recyclerView.setAdapter(adapter);

        // --- 3. DATABASE OBSERVER ---
        vaultViewModel = new ViewModelProvider(this).get(VaultViewModel.class);
        vaultViewModel.getAllCredentials().observe(this, credentials -> {
            allCredentials = credentials;
            adapter.setCredentials(credentials); // This ensures the list actually populates
        });

        // --- 4. SEARCH BAR LOGIC ---
        searchBar.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(android.text.Editable s) {
                filterVault(s.toString());
            }
        });

        // --- 5. NAV BAR LOGIC ---
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                recyclerView.setVisibility(View.GONE);
                fabAdd.setVisibility(View.GONE);
                searchBar.setVisibility(View.GONE);
                fragmentContainer.setVisibility(View.VISIBLE);
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new HomeFragment()).commit();
                return true;
            } else if (itemId == R.id.nav_vault) {
                fragmentContainer.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                fabAdd.setVisibility(View.VISIBLE);
                searchBar.setVisibility(View.VISIBLE);
                return true;
            } else if (itemId == R.id.nav_security || itemId == R.id.nav_about) {
                Toast.makeText(MainActivity.this, "Coming soon", Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });

        // --- 6. SET DEFAULT STATE (HOME SCREEN) ---
        recyclerView.setVisibility(View.GONE);
        fabAdd.setVisibility(View.GONE);
        searchBar.setVisibility(View.GONE);
        fragmentContainer.setVisibility(View.VISIBLE);
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new HomeFragment()).commit();

        // --- 7. ITEM ACTIONS (CLICK & SWIPE) ---
        adapter.setOnItemClickListener(credential -> {
            authenticateUser(() -> {
                try {
                    String decryptedPassword = EncryptionUtil.decryptPassword(credential.getEncryptedPassword(), credential.getEncryptionIv());
                    new MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle(credential.getTitle())
                            .setMessage("Username: " + credential.getUsername() + "\n\nPassword: " + decryptedPassword)
                            .setPositiveButton("Close", (dialog, which) -> dialog.dismiss())
                            .setNeutralButton("Copy Password", (dialog, which) -> {
                                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                                android.content.ClipData clip = android.content.ClipData.newPlainText("Vault Password", decryptedPassword);
                                clipboard.setPrimaryClip(clip);
                                Toast.makeText(MainActivity.this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
                            }).show();
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Decryption Failed!", Toast.LENGTH_SHORT).show();
                }
            });
        });

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) { return false; }
            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                com.example.passmanager.data.model.Credential credentialToDelete = adapter.getCredentialAt(position);
                adapter.notifyItemChanged(position);
                authenticateUser(() -> {
                    new MaterialAlertDialogBuilder(MainActivity.this)
                            .setTitle("Delete Password?")
                            .setMessage("Are you sure you want to delete " + credentialToDelete.getTitle() + "?")
                            .setPositiveButton("Delete", (dialog, which) -> {
                                vaultViewModel.delete(credentialToDelete);
                                Toast.makeText(MainActivity.this, "Deleted", Toast.LENGTH_SHORT).show();
                            })
                            .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                            .show();
                });
            }
        }).attachToRecyclerView(recyclerView);

        fabAdd.setOnClickListener(v -> {
            startActivity(new android.content.Intent(MainActivity.this, AddCredentialActivity.class));
        });
    }

    private void filterVault(String text) {
        if (adapter == null) return;
        List<com.example.passmanager.data.model.Credential> filteredList = new ArrayList<>();
        for (com.example.passmanager.data.model.Credential item : allCredentials) {
            String title = item.getTitle() != null ? item.getTitle().toLowerCase() : "";
            String username = item.getUsername() != null ? item.getUsername().toLowerCase() : "";
            if (title.contains(text.toLowerCase()) || username.contains(text.toLowerCase())) {
                filteredList.add(item);
            }
        }
        adapter.filterList(filteredList);
    }

    private void authenticateUser(Runnable onSuccessAction) {
        Executor executor = ContextCompat.getMainExecutor(this);
        BiometricPrompt biometricPrompt = new BiometricPrompt(MainActivity.this, executor, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                super.onAuthenticationSucceeded(result);
                onSuccessAction.run();
            }
        });
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Vault")
                .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG | androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL)
                .build();
        biometricPrompt.authenticate(promptInfo);
    }
}