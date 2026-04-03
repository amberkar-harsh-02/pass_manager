package com.example.passmanager.ui.main;

import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AnimationUtils;
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

import com.example.passmanager.BuildConfig;
import com.example.passmanager.PortraitCaptureActivity;
import com.example.passmanager.R;
import com.example.passmanager.SecurityUtil;
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
    private String pendingUsernamePayload = null;
    private String pendingTitlePayload = null;

    // Tracks the currently active tab to prevent re-click animations
    private int currentTabId = R.id.nav_home;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // --- 1. SECURITY: Block screenshots ---
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_main);

        // Fix Double Prompt from Gatekeeper
        getSharedPreferences("VaultSecurityPrefs", MODE_PRIVATE)
                .edit().putLong("LAST_BACKGROUND_TIME", 0).apply();

        // --- CHECK THREAT LEVEL (VOLATILE MEMORY) ---
        boolean isDuressMode = getIntent().getBooleanExtra("IS_DURESS_MODE", false);

        // --- 3. LINK ALL UI ELEMENTS TO IDs ---
        RecyclerView recyclerView = findViewById(R.id.recyclerView_credentials);
        FloatingActionButton fabAdd = findViewById(R.id.fab_add_password);
        searchBar = findViewById(R.id.edit_text_search);
        FrameLayout fragmentContainer = findViewById(R.id.fragment_container);
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);

        if (isDuressMode) {
            bottomNav.getMenu().removeItem(R.id.nav_security);
        }

        // --- 4. SETUP RECYCLERVIEW & ADAPTER ---
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);
        adapter = new CredentialAdapter();
        recyclerView.setAdapter(adapter);

        // --- 5. DATABASE OBSERVER ---
        vaultViewModel = new ViewModelProvider(this).get(VaultViewModel.class);
        vaultViewModel.getAllCredentials().observe(this, credentials -> {
            if (isDuressMode) {
                allCredentials = new java.util.ArrayList<>();
                adapter.setCredentials(new java.util.ArrayList<>());
            } else {
                allCredentials = credentials;
                adapter.setCredentials(credentials);
            }
        });

        // --- 6. SEARCH BAR LOGIC ---
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

        // --- 7. NAV BAR LOGIC ---
        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();

            if (itemId == currentTabId) return true;
            currentTabId = itemId;

            boolean wasFragmentContainerHidden = fragmentContainer.getVisibility() == View.GONE;

            if (itemId == R.id.nav_home) {
                recyclerView.setVisibility(View.GONE);
                fabAdd.setVisibility(View.GONE);
                searchBar.setVisibility(View.GONE);
                fragmentContainer.setVisibility(View.VISIBLE);
                if (wasFragmentContainerHidden) fragmentContainer.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up_enter));
                getSupportFragmentManager().beginTransaction().setCustomAnimations(R.anim.slide_up_enter, R.anim.fade_out_exit).replace(R.id.fragment_container, new HomeFragment()).commit();
                return true;
            } else if (itemId == R.id.nav_vault) {
                fragmentContainer.setVisibility(View.GONE);
                recyclerView.setVisibility(View.VISIBLE);
                searchBar.setVisibility(View.VISIBLE);
                recyclerView.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up_enter));
                fabAdd.setVisibility(isDuressMode ? View.GONE : View.VISIBLE);
                return true;
            } else if (itemId == R.id.nav_authenticator) {
                recyclerView.setVisibility(View.GONE);
                fabAdd.setVisibility(View.GONE);
                searchBar.setVisibility(View.GONE);
                fragmentContainer.setVisibility(View.VISIBLE);
                if (wasFragmentContainerHidden) fragmentContainer.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up_enter));
                getSupportFragmentManager().beginTransaction().setCustomAnimations(R.anim.slide_up_enter, R.anim.fade_out_exit).replace(R.id.fragment_container, new AuthenticatorFragment()).commit();
                return true;
            } else if (itemId == R.id.nav_security) {
                recyclerView.setVisibility(View.GONE);
                fabAdd.setVisibility(View.GONE);
                searchBar.setVisibility(View.GONE);
                fragmentContainer.setVisibility(View.VISIBLE);
                if (wasFragmentContainerHidden) fragmentContainer.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up_enter));
                getSupportFragmentManager().beginTransaction().setCustomAnimations(R.anim.slide_up_enter, R.anim.fade_out_exit).replace(R.id.fragment_container, new SecurityFragment()).commit();
                return true;
            } else if (itemId == R.id.nav_about) {
                recyclerView.setVisibility(View.GONE);
                fabAdd.setVisibility(View.GONE);
                searchBar.setVisibility(View.GONE);
                fragmentContainer.setVisibility(View.VISIBLE);
                if (wasFragmentContainerHidden) fragmentContainer.startAnimation(AnimationUtils.loadAnimation(this, R.anim.slide_up_enter));
                getSupportFragmentManager().beginTransaction().setCustomAnimations(R.anim.slide_up_enter, R.anim.fade_out_exit).replace(R.id.fragment_container, new AboutFragment()).commit();
                return true;
            }
            return false;
        });

        // --- 8. SET DEFAULT STATE (HOME SCREEN) ---
        recyclerView.setVisibility(View.GONE);
        fabAdd.setVisibility(View.GONE);
        searchBar.setVisibility(View.GONE);
        fragmentContainer.setVisibility(View.VISIBLE);
        getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new HomeFragment()).commit();

        // --- 9. ITEM ACTIONS (CLICK & SWIPE) ---
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
                            // --- QR CODE BRIDGE BUTTON ---
                            .setNegativeButton("Scan to Fill", (dialog, which) -> {
                                pendingInjectionPayload = decryptedPassword;
                                pendingUsernamePayload = credential.getUsername();

                                com.journeyapps.barcodescanner.ScanOptions options = new com.journeyapps.barcodescanner.ScanOptions();
                                options.setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE);
                                options.setPrompt("Scan the Ledger Extension QR Code");
                                options.setCameraId(0);
                                options.setBeepEnabled(false);
                                options.setCaptureActivity(PortraitCaptureActivity.class);
                                options.setOrientationLocked(true);

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
                adapter.notifyItemChanged(position);

                if (direction == ItemTouchHelper.RIGHT) {
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
                    authenticateUser(() -> {
                        android.content.Intent intent = new android.content.Intent(MainActivity.this, EditCredentialActivity.class);
                        intent.putExtra("CREDENTIAL_ID", targetCredential.getId());
                        intent.putExtra("CREDENTIAL_TITLE", targetCredential.getTitle());
                        intent.putExtra("CREDENTIAL_USERNAME", targetCredential.getUsername());
                        intent.putExtra("CREDENTIAL_ENCRYPTED_PW", targetCredential.getEncryptedPassword());
                        intent.putExtra("CREDENTIAL_IV", targetCredential.getEncryptionIv());
                        intent.putExtra("CREDENTIAL_TOTP_SECRET", targetCredential.getTotpSecret());
                        startActivity(intent);
                    });
                }
            }
        }).attachToRecyclerView(recyclerView);

        fabAdd.setOnClickListener(v -> showAddBottomSheet());
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

        if (lastBackgroundTime > 0) {
            android.widget.LinearLayout loadingOverlay = findViewById(R.id.layout_resume_loading);
            android.widget.ProgressBar progressBar = findViewById(R.id.progress_resume);

            findViewById(R.id.recyclerView_credentials).setVisibility(View.GONE);
            findViewById(R.id.fragment_container).setVisibility(View.GONE);
            findViewById(R.id.edit_text_search).setVisibility(View.GONE);
            findViewById(R.id.fab_add_password).setVisibility(View.GONE);

            loadingOverlay.setVisibility(View.VISIBLE);

            android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofInt(0, 100);
            animator.setDuration(1000);
            animator.addUpdateListener(animation -> progressBar.setProgress((int) animation.getAnimatedValue()));
            animator.start();

            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                loadingOverlay.setVisibility(View.GONE);
                long timeoutSetting = prefs.getLong("AUTO_LOCK_TIMEOUT", 0);
                long timeAway = System.currentTimeMillis() - lastBackgroundTime;

                if (timeoutSetting != -1 && timeAway >= timeoutSetting) {
                    authenticateUser(() -> {
                        restoreUiVisibility();
                        prefs.edit().putLong("LAST_BACKGROUND_TIME", 0).apply();
                    });
                } else {
                    restoreUiVisibility();
                    prefs.edit().putLong("LAST_BACKGROUND_TIME", 0).apply();
                }
            }, 1000);
        }
    }

    private void restoreUiVisibility() {
        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        int currentTab = bottomNav.getSelectedItemId();

        if (currentTab == R.id.nav_vault) {
            findViewById(R.id.recyclerView_credentials).setVisibility(View.VISIBLE);
            findViewById(R.id.edit_text_search).setVisibility(View.VISIBLE);
            boolean isDuressMode = getIntent().getBooleanExtra("IS_DURESS_MODE", false);
            if (!isDuressMode) findViewById(R.id.fab_add_password).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.fragment_container).setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        android.content.SharedPreferences prefs = getSharedPreferences("VaultSecurityPrefs", MODE_PRIVATE);
        prefs.edit().putLong("LAST_BACKGROUND_TIME", System.currentTimeMillis()).apply();
    }

    // --- OPTICAL KEY ENCRYPTION ENGINE (E2EE) ---
    private android.util.Pair<String, String> encryptForTransmission(String plaintext, String base64Key) throws Exception {
        // 1. Decode the AES key pulled from the optical QR code
        byte[] decodedKey = android.util.Base64.decode(base64Key, android.util.Base64.DEFAULT);
        javax.crypto.SecretKey originalKey = new javax.crypto.spec.SecretKeySpec(decodedKey, 0, decodedKey.length, "AES");

        // 2. Generate an ephemeral 12-byte IV for this specific transmission
        byte[] iv = new byte[12];
        new java.security.SecureRandom().nextBytes(iv);
        javax.crypto.spec.GCMParameterSpec parameterSpec = new javax.crypto.spec.GCMParameterSpec(128, iv);

        // 3. Encrypt the payload using AES-256-GCM
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, originalKey, parameterSpec);
        byte[] cipherText = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        // 4. Encode the IV and Ciphertext for safe network transit
        String base64Iv = android.util.Base64.encodeToString(iv, android.util.Base64.NO_WRAP);
        String base64Cipher = android.util.Base64.encodeToString(cipherText, android.util.Base64.NO_WRAP);

        return new android.util.Pair<>(base64Iv, base64Cipher);
    }

    // --- THE CAMERA SCANNER & NETWORK BRIDGE ---
    private final androidx.activity.result.ActivityResultLauncher<com.journeyapps.barcodescanner.ScanOptions> barcodeLauncher =
            registerForActivityResult(new com.journeyapps.barcodescanner.ScanContract(), result -> {

                if (result.getContents() == null) {
                    android.widget.Toast.makeText(this, "Scan cancelled", android.widget.Toast.LENGTH_SHORT).show();
                    pendingInjectionPayload = null;
                    pendingUsernamePayload = null;
                    pendingTitlePayload = null;
                } else {
                    final String capturedPass = pendingInjectionPayload;
                    final String capturedUser = pendingUsernamePayload;
                    final String capturedTitle = pendingTitlePayload;

                    pendingInjectionPayload = null;
                    pendingUsernamePayload = null;
                    pendingTitlePayload = null;

                    if (capturedPass == null || capturedUser == null) {
                        // Scan without pending payload
                    } else if (capturedTitle != null) {
                        try {
                            android.util.Pair<String, String> encryptedData =
                                    com.example.passmanager.security.EncryptionUtil.encryptPassword(capturedPass);

                            com.example.passmanager.data.model.Credential newAccount =
                                    new com.example.passmanager.data.model.Credential(capturedTitle, capturedUser, encryptedData.first, encryptedData.second, 3, null);

                            com.example.passmanager.ui.viewmodel.VaultViewModel vaultViewModel =
                                    new androidx.lifecycle.ViewModelProvider(MainActivity.this).get(com.example.passmanager.ui.viewmodel.VaultViewModel.class);

                            vaultViewModel.insert(newAccount);
                            android.widget.Toast.makeText(MainActivity.this, "Saved " + capturedTitle + " to Vault!", android.widget.Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            android.widget.Toast.makeText(MainActivity.this, "Encryption Error!", android.widget.Toast.LENGTH_SHORT).show();
                        }
                    }

                    try {
                        // 1. Parse the NEW armored QR Code JSON
                        org.json.JSONObject qrData = new org.json.JSONObject(result.getContents());
                        String targetRoom = qrData.getString("room");
                        String opticalKey = qrData.getString("key"); // Grabbing the air-gapped key!

                        // 2. Encrypt the password using the Optical Key
                        android.util.Pair<String, String> encryptedTransmission = encryptForTransmission(capturedPass, opticalKey);
                        String transmissionIv = encryptedTransmission.first;
                        String transmissionPayload = encryptedTransmission.second;

                        // 3. Connect using hidden key
                        String RELAY_URL = "wss://s16353.nyc1.piesocket.com/v3/1?api_key=" + BuildConfig.PIESOCKET_API_KEY + "&notify_self=1";
                        okhttp3.OkHttpClient client = new okhttp3.OkHttpClient();
                        okhttp3.Request request = new okhttp3.Request.Builder().url(RELAY_URL).build();

                        android.widget.Toast.makeText(this, "Firing Ciphertext...", android.widget.Toast.LENGTH_SHORT).show();

                        // 4. Fire the BLIND payload over WebSockets
                        client.newWebSocket(request, new okhttp3.WebSocketListener() {
                            @Override
                            public void onOpen(okhttp3.WebSocket webSocket, okhttp3.Response response) {
                                try {
                                    org.json.JSONObject payloadWrapper = new org.json.JSONObject();
                                    payloadWrapper.put("room", targetRoom);
                                    payloadWrapper.put("username", capturedUser);

                                    // SENDING ONLY GIBBERISH TO PIESOCKET
                                    payloadWrapper.put("payload", transmissionPayload);
                                    payloadWrapper.put("iv", transmissionIv);

                                    webSocket.send(payloadWrapper.toString());

                                    runOnUiThread(() -> android.widget.Toast.makeText(MainActivity.this, "Ciphertext Delivered to Relay!", android.widget.Toast.LENGTH_SHORT).show());
                                } catch (Exception e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onFailure(okhttp3.WebSocket webSocket, Throwable t, okhttp3.Response response) {
                                runOnUiThread(() -> android.widget.Toast.makeText(MainActivity.this, "Network Bridge Failed.", android.widget.Toast.LENGTH_SHORT).show());
                            }
                        });

                    } catch (Exception e) {
                        android.util.Log.e("LedgerBridge", "E2EE Setup Failed: ", e);
                        android.widget.Toast.makeText(this, "Error: " + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                    }
                }
            });

    private void showSignUpInjectorDialog() {
        android.widget.LinearLayout layout = new android.widget.LinearLayout(this);
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        android.widget.EditText titleInput = new android.widget.EditText(this);
        titleInput.setHint("Website/App Name (e.g., GitHub)");
        layout.addView(titleInput);

        android.widget.EditText usernameInput = new android.widget.EditText(this);
        usernameInput.setHint("Username / Email");
        android.widget.LinearLayout.LayoutParams params = new android.widget.LinearLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 20, 0, 0);
        usernameInput.setLayoutParams(params);
        layout.addView(usernameInput);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                .setTitle("Sign-Up Injector")
                .setMessage("Enter the details. VaultShield will generate a secure password, save it, and inject it.")
                .setView(layout)
                .setPositiveButton("Generate & Scan", (dialog, which) -> {
                    String newTitle = titleInput.getText().toString().trim();
                    String newUser = usernameInput.getText().toString().trim();

                    if (newTitle.isEmpty() || newUser.isEmpty()) {
                        android.widget.Toast.makeText(this, "Title and Username required", android.widget.Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String newPass = SecurityUtil.generateSecurePassword();

                    pendingTitlePayload = newTitle;
                    pendingUsernamePayload = newUser;
                    pendingInjectionPayload = newPass;

                    com.journeyapps.barcodescanner.ScanOptions options = new com.journeyapps.barcodescanner.ScanOptions();
                    options.setDesiredBarcodeFormats(com.journeyapps.barcodescanner.ScanOptions.QR_CODE);
                    options.setPrompt("Scan Extension QR to Inject New Account");
                    options.setCameraId(0);
                    options.setBeepEnabled(false);
                    options.setCaptureActivity(PortraitCaptureActivity.class);
                    options.setOrientationLocked(true);

                    barcodeLauncher.launch(options);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showAddBottomSheet() {
        com.google.android.material.bottomsheet.BottomSheetDialog bottomSheetDialog =
                new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        android.view.View sheetView = getLayoutInflater().inflate(R.layout.bottom_sheet_add, null);
        bottomSheetDialog.setContentView(sheetView);

        android.widget.LinearLayout btnManual = sheetView.findViewById(R.id.btn_manual_entry);
        android.widget.LinearLayout btnGenerate = sheetView.findViewById(R.id.btn_generate_inject);

        btnManual.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            startActivity(new android.content.Intent(MainActivity.this, com.example.passmanager.ui.main.AddCredentialActivity.class));
        });

        btnGenerate.setOnClickListener(v -> {
            bottomSheetDialog.dismiss();
            showSignUpInjectorDialog();
        });

        bottomSheetDialog.show();
    }
}