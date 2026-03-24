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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SwitchCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.example.passmanager.R;
import com.example.passmanager.data.model.Credential;
import com.example.passmanager.security.BackupEncryptionUtil;
import com.example.passmanager.security.EncryptionUtil;
import com.example.passmanager.ui.viewmodel.VaultViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SecurityFragment extends Fragment {

    private SharedPreferences sharedPreferences;
    private TextView textAutoLockStatus;
    private List<com.example.passmanager.data.model.AuditLog> currentLogs = new java.util.ArrayList<>();
    private List<Credential> currentVault = new ArrayList<>();

    // Elevated to class level so our Import function can use it
    private VaultViewModel vaultViewModel;

    private AutofillManager autofillManager;
    private SwitchMaterial autofillSwitch;

    private final String[] timeoutOptions = {"Immediately", "1 Minute", "5 Minutes", "Never"};
    private final long[] timeoutValues = {0, 60000, 300000, -1};

    // --- SAF FILE PICKER LAUNCHERS ---

    // 1. Export (Save File) Launcher
    private final ActivityResultLauncher<Intent> exportFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == requireActivity().RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    promptForBackupPassword(uri, true);
                }
            });

    // 2. Import (Open File) Launcher
    private final ActivityResultLauncher<Intent> importFileLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == requireActivity().RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    promptForBackupPassword(uri, false);
                }
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_security, container, false);

        sharedPreferences = requireActivity().getSharedPreferences("VaultSecurityPrefs", Context.MODE_PRIVATE);
        textAutoLockStatus = view.findViewById(R.id.text_auto_lock_status);

        // --- CONNECT TO DATABASE FOR THE AUDIT ---
        vaultViewModel = new ViewModelProvider(requireActivity()).get(VaultViewModel.class);
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

        // --- NEW: BACKUP & RESTORE BUTTONS ---
        View btnExportVault = view.findViewById(R.id.btn_export_vault);
        if(btnExportVault != null) btnExportVault.setOnClickListener(v -> triggerExportFlow());

        View btnImportVault = view.findViewById(R.id.btn_import_vault);
        if(btnImportVault != null) btnImportVault.setOnClickListener(v -> triggerImportFlow());

        // --- 4. AUTOFILL TOGGLE LOGIC ---
        autofillSwitch = view.findViewById(R.id.switch_autofill);

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            autofillManager = requireContext().getSystemService(AutofillManager.class);
        }

        autofillSwitch.setOnClickListener(v -> {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                // Read from both the manager and the raw database to be 100% sure
                boolean isCurrentlyEnabled = autofillManager != null && autofillManager.hasEnabledAutofillServices();
                String defaultService = Settings.Secure.getString(requireContext().getContentResolver(), "autofill_service");
                if (defaultService != null && defaultService.contains(requireContext().getPackageName())) {
                    isCurrentlyEnabled = true;
                }

                if (!isCurrentlyEnabled) {
                    autofillSwitch.setChecked(false); // Force off visually until confirmed
                    Intent intent = new Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE);
                    intent.setData(Uri.parse("package:" + requireContext().getPackageName()));
                    startActivity(intent);
                } else {
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

    // --- SAF BACKUP & RESTORE LOGIC ---

    private void triggerExportFlow() {
        if (currentVault.isEmpty()) {
            Toast.makeText(getContext(), "Vault is empty. Nothing to export.", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/octet-stream");
        intent.putExtra(Intent.EXTRA_TITLE, "vault_backup.ledger");
        exportFileLauncher.launch(intent);
    }

    private void triggerImportFlow() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*"); // Accept all files, but we expect .ledger
        importFileLauncher.launch(intent);
    }

    private void promptForBackupPassword(Uri uri, boolean isExporting) {
        final EditText input = new EditText(requireContext());
        input.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(isExporting ? "Create Backup Password" : "Enter Backup Password");

        android.widget.FrameLayout container = new android.widget.FrameLayout(requireContext());
        android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(50, 20, 50, 0);
        input.setLayoutParams(params);
        container.addView(input);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(isExporting ? "Encrypt Backup" : "Decrypt Backup")
                .setMessage(isExporting ? "Create a strong password. If you lose this, your backup cannot be recovered." : "Enter the password used to encrypt this file.")
                .setView(container)
                .setPositiveButton(isExporting ? "Encrypt & Save" : "Decrypt & Merge", (dialog, which) -> {
                    String password = input.getText().toString();
                    if (password.length() < 4) {
                        Toast.makeText(getContext(), "Password must be at least 4 characters", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (isExporting) {
                        executeExport(uri, password);
                    } else {
                        executeImport(uri, password);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void executeExport(Uri fileUri, String password) {
        try {
            // 1. Serialize Room Data to JSON
            JSONArray jsonArray = new JSONArray();
            for (Credential c : currentVault) {
                JSONObject obj = new JSONObject();
                obj.put("title", c.getTitle());
                obj.put("username", c.getUsername());
                obj.put("encryptedPassword", c.getEncryptedPassword());
                obj.put("encryptionIv", c.getEncryptionIv());
                obj.put("healthScore", c.getHealthScore());
                obj.put("totpSecret", c.getTotpSecret() != null ? c.getTotpSecret() : "");
                jsonArray.put(obj);
            }

            // 2. Encrypt the JSON String
            byte[] encryptedBytes = BackupEncryptionUtil.encryptBackup(jsonArray.toString(), password);

            // 3. Write to the SAF File
            OutputStream os = requireContext().getContentResolver().openOutputStream(fileUri);
            if (os != null) {
                os.write(encryptedBytes);
                os.close();
                Toast.makeText(getContext(), "Backup Encrypted and Saved Successfully!", Toast.LENGTH_LONG).show();
            }

        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Export Failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void executeImport(Uri fileUri, String password) {
        try {
            // 1. Read the bytes from the SAF File
            InputStream is = requireContext().getContentResolver().openInputStream(fileUri);
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[16384];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            is.close();
            byte[] fileBytes = buffer.toByteArray();

            // 2. Decrypt back to JSON
            String jsonString = BackupEncryptionUtil.decryptBackup(fileBytes, password);

            // 3. Parse JSON and Insert into DB (The Merge Option)
            JSONArray jsonArray = new JSONArray(jsonString);
            int importedCount = 0;

            for (int i = 0; i < jsonArray.length(); i++) {
                JSONObject obj = jsonArray.getJSONObject(i);

                String totp = obj.optString("totpSecret", "");
                if (totp.isEmpty()) totp = null; // Clean up empty strings to null for Room

                Credential c = new Credential(
                        obj.getString("title"),
                        obj.getString("username"),
                        obj.getString("encryptedPassword"),
                        obj.getString("encryptionIv"),
                        obj.getInt("healthScore"),
                        totp
                );

                // Insert alongside existing data!
                vaultViewModel.insert(c);
                importedCount++;
            }

            Toast.makeText(getContext(), "Successfully merged " + importedCount + " accounts into your vault!", Toast.LENGTH_LONG).show();

        } catch (javax.crypto.AEADBadTagException e) {
            Toast.makeText(getContext(), "Import Failed: Incorrect Password!", Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(getContext(), "Import Failed: File might be corrupted.", Toast.LENGTH_LONG).show();
        }
    }

    // Every time the user looks at this tab, sync the switch with the actual system truth
    @Override
    public void onResume() {
        super.onResume();
        syncAutofillSwitchState();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            syncAutofillSwitchState();
        }
    }

    private void syncAutofillSwitchState() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            boolean isLedgerActive = false;

            // 1. The Standard Check
            if (autofillManager != null && autofillManager.hasEnabledAutofillServices()) {
                isLedgerActive = true;
            }

            // 2. THE SILVER BULLET: Check System Settings Directly
            // This reads the raw OS database, completely bypassing Android's caching bugs.
            String defaultService = Settings.Secure.getString(requireContext().getContentResolver(), "autofill_service");
            if (defaultService != null && defaultService.contains(requireContext().getPackageName())) {
                isLedgerActive = true;
            }

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