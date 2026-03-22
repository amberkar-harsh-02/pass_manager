package com.example.passmanager.ui.main;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.autofill.AutofillManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.passmanager.R;
import com.example.passmanager.data.model.Credential;
import com.example.passmanager.security.EncryptionUtil;
import com.example.passmanager.ui.viewmodel.VaultViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SecurityFragment extends Fragment {

    private SharedPreferences sharedPreferences;
    private TextView textAutoLockStatus;
    private List<com.example.passmanager.data.model.AuditLog> currentLogs = new java.util.ArrayList<>();
    private List<Credential> currentVault = new ArrayList<>();

    private AutofillManager autofillManager;
    private SwitchMaterial autofillSwitch;

    private final String[] timeoutOptions = {"Immediately", "1 Minute", "5 Minutes", "Never"};
    private final long[] timeoutValues = {0, 60000, 300000, -1};

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_security, container, false);

        sharedPreferences = requireActivity().getSharedPreferences("VaultSecurityPrefs", Context.MODE_PRIVATE);
        textAutoLockStatus = view.findViewById(R.id.text_auto_lock_status);

        // --- CONNECT TO DATABASE FOR THE AUDIT ---
        VaultViewModel vaultViewModel = new ViewModelProvider(requireActivity()).get(VaultViewModel.class);
        vaultViewModel.getAllCredentials().observe(getViewLifecycleOwner(), credentials -> {
            if (credentials != null) {
                currentVault = credentials;
            }
        });

        com.example.passmanager.data.local.VaultDatabase.getDatabase(requireContext())
                .auditLogDao().getAllLogs().observe(getViewLifecycleOwner(), logs -> {
                    if (logs != null) {
                        currentLogs = logs;
                    }
                });

        // --- 1. STEALTH MODE TOGGLE ---
        SwitchMaterial switchStealthMode = view.findViewById(R.id.switch_stealth_mode);
        boolean isStealthEnabled = sharedPreferences.getBoolean("STEALTH_MODE", true);
        switchStealthMode.setChecked(isStealthEnabled);

        switchStealthMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            sharedPreferences.edit().putBoolean("STEALTH_MODE", isChecked).apply();
            if (isChecked) {
                requireActivity().getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
                Toast.makeText(getContext(), "Stealth Mode Engaged", Toast.LENGTH_SHORT).show();
            } else {
                requireActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                Toast.makeText(getContext(), "Stealth Mode Disabled", Toast.LENGTH_SHORT).show();
            }
        });

        // --- 2. AUTO-LOCK TIMEOUT ---
        long currentTimeout = sharedPreferences.getLong("AUTO_LOCK_TIMEOUT", 0);
        updateAutoLockText(currentTimeout);

        View btnAutoLock = view.findViewById(R.id.btn_auto_lock);
        btnAutoLock.setOnClickListener(v -> showTimeoutDialog());

        // --- 3. PASSWORD REUSE SCANNER ---
        View btnReuseScanner = view.findViewById(R.id.btn_reuse_scanner);
        btnReuseScanner.setOnClickListener(v -> runReuseAudit());

        View btnAuditLog = view.findViewById(R.id.btn_audit_log);
        btnAuditLog.setOnClickListener(v -> showAuditLogDialog());

        View btnSetupDuress = view.findViewById(R.id.btn_setup_duress);
        btnSetupDuress.setOnClickListener(v -> showDuressPinDialog());

        // --- 4. AUTOFILL TOGGLE LOGIC ---
        autofillSwitch = view.findViewById(R.id.switch_autofill);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            autofillManager = requireContext().getSystemService(AutofillManager.class);
        }

        autofillSwitch.setOnClickListener(v -> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                boolean isCurrentlyEnabled = autofillManager != null && autofillManager.hasEnabledAutofillServices();

                if (!isCurrentlyEnabled) {
                    // Turn it ON: Send them to Settings
                    autofillSwitch.setChecked(false); // Keep it off visually until confirmed
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE);
                    intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                    startActivity(intent);
                } else {
                    // Turn it OFF: Force kill the service at the OS level!
                    if (autofillManager != null) {
                        autofillManager.disableAutofillServices();
                        autofillSwitch.setChecked(false);
                        Toast.makeText(getContext(), "Autofill disabled at system level.", Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });

        return view;
    }

    // Every time the user looks at this tab, sync the switch with the actual system truth
    @Override
    public void onResume() {
        super.onResume();
        syncAutofillSwitchState();
    }

    private void syncAutofillSwitchState() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && autofillManager != null) {
            boolean isLedgerActive = autofillManager.hasEnabledAutofillServices();
            if (autofillSwitch != null) {
                autofillSwitch.setChecked(isLedgerActive);
            }
        }
    }

    private void showTimeoutDialog() {
        long currentTimeout = sharedPreferences.getLong("AUTO_LOCK_TIMEOUT", 0);
        int checkedItem = 0;
        for (int i = 0; i < timeoutValues.length; i++) {
            if (timeoutValues[i] == currentTimeout) {
                checkedItem = i;
                break;
            }
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Auto-Lock Timeout")
                .setSingleChoiceItems(timeoutOptions, checkedItem, (dialog, which) -> {
                    long selectedTimeout = timeoutValues[which];
                    sharedPreferences.edit().putLong("AUTO_LOCK_TIMEOUT", selectedTimeout).apply();
                    updateAutoLockText(selectedTimeout);
                    Toast.makeText(getContext(), "Timeout updated", Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                })
                .show();
    }

    private void updateAutoLockText(long timeoutInMillis) {
        if (timeoutInMillis == 0) textAutoLockStatus.setText("Immediately");
        else if (timeoutInMillis == 60000) textAutoLockStatus.setText("1 Minute");
        else if (timeoutInMillis == 300000) textAutoLockStatus.setText("5 Minutes");
        else if (timeoutInMillis == -1) textAutoLockStatus.setText("Never");
    }

    private void runReuseAudit() {
        if (currentVault.size() < 2) {
            Toast.makeText(getContext(), "Not enough credentials to run an audit.", Toast.LENGTH_SHORT).show();
            return;
        }

        HashMap<String, List<String>> passwordMap = new HashMap<>();

        try {
            for (Credential cred : currentVault) {
                String decrypted = EncryptionUtil.decryptPassword(cred.getEncryptedPassword(), cred.getEncryptionIv());

                if (!passwordMap.containsKey(decrypted)) {
                    passwordMap.put(decrypted, new ArrayList<>());
                }
                passwordMap.get(decrypted).add(cred.getTitle());
            }

            StringBuilder report = new StringBuilder();
            int reuseCount = 0;

            for (Map.Entry<String, List<String>> entry : passwordMap.entrySet()) {
                if (entry.getValue().size() > 1) {
                    reuseCount++;
                    report.append("• Reused across: ").append(String.join(", ", entry.getValue())).append("\n\n");
                }
            }

            if (reuseCount == 0) {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Audit Complete")
                        .setMessage("Excellent security posture! No reused passwords found in your vault.")
                        .setPositiveButton("Close", null)
                        .show();
            } else {
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Vulnerability: Password Reuse")
                        .setMessage("We found the same password reused across multiple accounts. This is a critical risk if one of these services is breached.\n\n" + report.toString())
                        .setPositiveButton("I will fix these", null)
                        .show();
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Audit Failed: Decryption Error", Toast.LENGTH_SHORT).show();
        }
    }

    private void showAuditLogDialog() {
        if (currentLogs.isEmpty()) {
            Toast.makeText(getContext(), "No logs available.", Toast.LENGTH_SHORT).show();
            return;
        }

        StringBuilder report = new StringBuilder();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy - HH:mm:ss", java.util.Locale.getDefault());

        int limit = Math.min(currentLogs.size(), 20);
        for (int i = 0; i < limit; i++) {
            com.example.passmanager.data.model.AuditLog log = currentLogs.get(i);

            String date = sdf.format(new java.util.Date(log.getTimestamp()));
            String status = log.isSuccessful() ? "✅ SUCCESS" : "❌ FAILED";

            report.append(date).append("\n");
            report.append("Method: ").append(log.getEventType()).append(" | ").append(status).append("\n\n");
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Access Audit Log")
                .setMessage(report.toString().trim())
                .setPositiveButton("Close", null)
                .show();
    }

    private void showDuressPinDialog() {
        final android.widget.EditText input = new android.widget.EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER | android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        input.setHint("Enter 4-digit PIN");
        input.setFilters(new android.text.InputFilter[] { new android.text.InputFilter.LengthFilter(4) });

        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(50, 20, 50, 0);
        input.setLayoutParams(params);
        container.addView(input);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Set Duress PIN")
                .setMessage("Make sure this is different from your Master PIN.")
                .setView(container)
                .setPositiveButton("Save", (dialog, which) -> {
                    String pin = input.getText().toString();
                    if(pin.length() == 4) {
                        String hashed = hashPin(pin);
                        sharedPreferences.edit().putString("DURESS_PIN_HASH", hashed).apply();
                        android.widget.Toast.makeText(getContext(), "Duress PIN Secured", android.widget.Toast.LENGTH_SHORT).show();
                    } else {
                        android.widget.Toast.makeText(getContext(), "PIN must be exactly 4 digits", android.widget.Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String hashPin(String plainPin) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            digest.update("LedgerVaultSalt2026".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] hash = digest.digest(plainPin.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}