package com.example.passmanager.ui.main;

import android.graphics.Bitmap;
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

import com.example.passmanager.PortraitCaptureActivity;
import com.example.passmanager.QRCodeHelper;
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

    private String pendingInjectionPayload = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // SECURITY: Block screenshots
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        // 1. PAINT THE SCREEN FIRST!
        setContentView(R.layout.activity_main);

        // Fix Double Prompt from Gatekeeper
        getSharedPreferences("VaultSecurityPrefs", MODE_PRIVATE)
                .edit().putLong("LAST_BACKGROUND_TIME", 0).apply();

        // --- CHECK THREAT LEVEL ---
        // --- CHECK THREAT LEVEL (VOLATILE MEMORY) ---
        boolean isDuressMode = getIntent().getBooleanExtra("IS_DURESS_MODE", false);

        // --- 1. LINK ALL UI ELEMENTS TO IDs ---
        RecyclerView recyclerView = findViewById(R.id.recyclerView_credentials);
        FloatingActionButton fabAdd = findViewById(R.id.fab_add_password);
        searchBar = findViewById(R.id.edit_text_search);
        FrameLayout fragmentContainer = findViewById(R.id.fragment_container);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        // THE PHANTOM TAB: Dynamically erase the Security tab from the UI if compromised
        if (isDuressMode) {
            bottomNav.getMenu().removeItem(R.id.nav_security);
        }

        // --- 2. SETUP RECYCLERVIEW & ADAPTER ---
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        adapter = new CredentialAdapter();
        recyclerView.setAdapter(adapter);

        // --- 3. DATABASE OBSERVER ---
        vaultViewModel = new ViewModelProvider(this).get(VaultViewModel.class);
        vaultViewModel.getAllCredentials().observe(this, credentials -> {
            if (isDuressMode) {
                // THE ILLUSION: Give them a completely empty vault
                allCredentials = new java.util.ArrayList<>();
                adapter.setCredentials(new java.util.ArrayList<>());
            } else {
                // REAL MODE: Load actual passwords
                allCredentials = credentials;
                adapter.setCredentials(credentials);
            }
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
                searchBar.setVisibility(View.VISIBLE);

                // Only show the Add button if we are NOT in Duress Mode
                if (!isDuressMode) {
                    fabAdd.setVisibility(View.VISIBLE);
                } else {
                    fabAdd.setVisibility(View.GONE);
                }
                return true;
            } else if (itemId == R.id.nav_security) {
                // --- NEW: Load the Security Fragment ---
                recyclerView.setVisibility(View.GONE);
                fabAdd.setVisibility(View.GONE);
                searchBar.setVisibility(View.GONE);
                fragmentContainer.setVisibility(View.VISIBLE);

                getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new SecurityFragment())
                        .commit();
                return true;
            } else if (itemId == R.id.nav_about) {
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
                            .setNeutralButton("Copy", (dialog, which) -> {
                                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                                android.content.ClipData clip = android.content.ClipData.newPlainText("Vault Password", decryptedPassword);
                                clipboard.setPrimaryClip(clip);
                                android.widget.Toast.makeText(MainActivity.this, "Copied to clipboard", android.widget.Toast.LENGTH_SHORT).show();
                            })
                            // --- NEW: QR CODE BRIDGE BUTTON ---
                            .setNegativeButton("Scan to Fill", (dialog, which) -> {
                                // 1. Save the decrypted password to RAM
                                pendingInjectionPayload = decryptedPassword;

                                // 2. Configure the Viewfinder
                                com.journeyapps.barcodescanner.ScanOptions options = new com.journeyapps.barcodescanner.ScanOptions();
                                options.setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE);
                                options.setPrompt("Scan the Ledger Extension QR Code");
                                options.setCameraId(0);
                                options.setBeepEnabled(false);

                                // --- NEW: FORCE PORTRAIT MODE ---
                                options.setCaptureActivity(PortraitCaptureActivity.class);
                                options.setOrientationLocked(true);

                                // 3. Launch the Camera
                                barcodeLauncher.launch(options);
                            }).show();

                } catch (Exception e) {
                    android.widget.Toast.makeText(MainActivity.this, "Decryption Failed!", android.widget.Toast.LENGTH_SHORT).show();
                }
            });
        });

        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                int position = viewHolder.getAdapterPosition();
                com.example.passmanager.data.model.Credential targetCredential = adapter.getCredentialAt(position);

                // Instantly bounce the item back into the list while we process the action
                adapter.notifyItemChanged(position);

                if (direction == ItemTouchHelper.RIGHT) {
                    // --- SWIPE RIGHT: DELETE ---
                    authenticateUser(() -> {
                        new MaterialAlertDialogBuilder(MainActivity.this)
                                .setTitle("Delete Password?")
                                .setMessage("Are you sure you want to delete " + targetCredential.getTitle() + "?")
                                .setPositiveButton("Delete", (dialog, which) -> {
                                    vaultViewModel.delete(targetCredential);
                                    Toast.makeText(MainActivity.this, "Deleted", Toast.LENGTH_SHORT).show();
                                })
                                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                                .show();
                    });

                } else if (direction == ItemTouchHelper.LEFT) {
                    // --- SWIPE LEFT: EDIT ---
                    authenticateUser(() -> {
                        android.content.Intent intent = new android.content.Intent(MainActivity.this, EditCredentialActivity.class);

                        // Package up the old data to send to the Edit Screen
                        intent.putExtra("CREDENTIAL_ID", targetCredential.getId());
                        intent.putExtra("CREDENTIAL_TITLE", targetCredential.getTitle());
                        intent.putExtra("CREDENTIAL_USERNAME", targetCredential.getUsername());
                        intent.putExtra("CREDENTIAL_ENCRYPTED_PW", targetCredential.getEncryptedPassword());
                        intent.putExtra("CREDENTIAL_IV", targetCredential.getEncryptionIv());

                        startActivity(intent);
                    });
                }
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

    @Override
    protected void onStart() {
        super.onStart();
        android.content.SharedPreferences prefs = getSharedPreferences("VaultSecurityPrefs", MODE_PRIVATE);

        long lastBackgroundTime = prefs.getLong("LAST_BACKGROUND_TIME", 0);
        long timeoutSetting = prefs.getLong("AUTO_LOCK_TIMEOUT", 0); // Default is 0 (Immediately)

        // If the user just opened the app for the first time, lastBackgroundTime is 0.
        // We only check for a lock if they are returning from the background.
        if (lastBackgroundTime > 0 && timeoutSetting != -1) {

            long timeAway = System.currentTimeMillis() - lastBackgroundTime;

            if (timeAway >= timeoutSetting) {
                // LOCK TRIGGERED!
                // 1. Hide the Vault contents immediately to prevent visual leaks
                findViewById(R.id.recyclerView_credentials).setVisibility(View.GONE);
                findViewById(R.id.fragment_container).setVisibility(View.GONE);

                // 2. Throw the Biometric Prompt
                authenticateUser(() -> {
                    // 3. On Success: Restore the UI based on what tab they were on
                    BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
                    bottomNav.setSelectedItemId(bottomNav.getSelectedItemId()); // Refreshes current tab

                    // Reset the background timer so it doesn't instantly lock again
                    prefs.edit().putLong("LAST_BACKGROUND_TIME", 0).apply();
                });
            }
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // The moment the app goes to the background, stamp the current time
        android.content.SharedPreferences prefs = getSharedPreferences("VaultSecurityPrefs", MODE_PRIVATE);
        prefs.edit().putLong("LAST_BACKGROUND_TIME", System.currentTimeMillis()).apply();
    }

    // --- THE CAMERA SCANNER & NETWORK BRIDGE ---
    private final androidx.activity.result.ActivityResultLauncher<com.journeyapps.barcodescanner.ScanOptions> barcodeLauncher =
            registerForActivityResult(new com.journeyapps.barcodescanner.ScanContract(), result -> {

                if (result.getContents() == null) {
                    android.widget.Toast.makeText(this, "Scan cancelled", android.widget.Toast.LENGTH_SHORT).show();
                    pendingInjectionPayload = null;
                } else {
                    // --- THE FIX: CAPTURE AND WIPE SYNCHRONOUSLY ---
                    final String capturedPayload = pendingInjectionPayload;
                    pendingInjectionPayload = null; // Safely wipe global buffer immediately

                    if (capturedPayload == null) {
                        android.widget.Toast.makeText(this, "Error: Payload lost from memory.", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }

                    try {
                        // 1. Parse the QR Code JSON
                        org.json.JSONObject qrData = new org.json.JSONObject(result.getContents());
                        String targetRoom = qrData.getString("room");

                        // 2. Connect to your Private Pub/Sub Relay (Ensure your PieSocket URL is here!)
                        String RELAY_URL = "wss://s16271.nyc1.piesocket.com/v3/1?api_key=3lorUztBvwOhTfiMNJ4W3CAWJiCRAXEjWzwBciIS&notify_self=1";
                        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                        okhttp3.Request request = new okhttp3.Request.Builder().url(RELAY_URL).build();

                        android.widget.Toast.makeText(this, "Firing Payload...", android.widget.Toast.LENGTH_SHORT).show();

                        // 3. Fire the payload over WebSockets
                        client.newWebSocket(request, new okhttp3.WebSocketListener() {
                            @Override
                            public void onOpen(okhttp3.WebSocket webSocket, okhttp3.Response response) {
                                try {
                                    org.json.JSONObject payloadWrapper = new org.json.JSONObject();
                                    payloadWrapper.put("room", targetRoom);

                                    // USE THE CAPTURED PAYLOAD HERE
                                    payloadWrapper.put("password", capturedPayload);

                                    webSocket.send(payloadWrapper.toString());

                                    runOnUiThread(() -> android.widget.Toast.makeText(MainActivity.this, "Payload Delivered to Relay!", android.widget.Toast.LENGTH_SHORT).show());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onFailure(okhttp3.WebSocket webSocket, Throwable t, okhttp3.Response response) {
                                if (t instanceof java.io.EOFException) {
                                    android.util.Log.w("LedgerBridge", "Server closed connection early, but payload may have fired.");
                                } else {
                                    android.util.Log.e("LedgerBridge", "FATAL NETWORK CRASH: ", t);
                                    runOnUiThread(() -> android.widget.Toast.makeText(MainActivity.this, "Network Bridge Failed.", android.widget.Toast.LENGTH_SHORT).show());
                                }
                            }
                        });

                    } catch (Exception e) {
                        // Print the EXACT reason it failed to your Logcat
                        android.util.Log.e("LedgerBridge", "Setup Failed: ", e);
                        android.widget.Toast.makeText(this, "Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                    }
                }
            });
}