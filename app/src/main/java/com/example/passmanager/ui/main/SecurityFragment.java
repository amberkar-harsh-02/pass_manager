package com.example.passmanager.ui.main;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
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

    // NEW: Store the vault data in memory for the audit
    private List<Credential> currentVault = new ArrayList<>();

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

        return view;
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

    // --- NEW: THE THREAT INTELLIGENCE ENGINE ---
    private void runReuseAudit() {
        if (currentVault.size() < 2) {
            Toast.makeText(getContext(), "Not enough credentials to run an audit.", Toast.LENGTH_SHORT).show();
            return;
        }

        // Map format: <DecryptedPassword, List of Account Titles>
        HashMap<String, List<String>> passwordMap = new HashMap<>();

        try {
            // 1. Decrypt and Group
            for (Credential cred : currentVault) {
                String decrypted = EncryptionUtil.decryptPassword(cred.getEncryptedPassword(), cred.getEncryptionIv());

                if (!passwordMap.containsKey(decrypted)) {
                    passwordMap.put(decrypted, new ArrayList<>());
                }
                passwordMap.get(decrypted).add(cred.getTitle());
            }

            // 2. Analyze the grouped data
            StringBuilder report = new StringBuilder();
            int reuseCount = 0;

            for (Map.Entry<String, List<String>> entry : passwordMap.entrySet()) {
                if (entry.getValue().size() > 1) {
                    reuseCount++;
                    // Append the titles of the accounts sharing this password
                    report.append("• Reused across: ").append(String.join(", ", entry.getValue())).append("\n\n");
                }
            }

            // 3. Display the Intelligence Report
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
        // Format the raw milliseconds into a clean Date/Time
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("MMM dd, yyyy - HH:mm:ss", java.util.Locale.getDefault());

        // Loop through the most recent 20 logs to prevent the dialog from getting too massive
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
}