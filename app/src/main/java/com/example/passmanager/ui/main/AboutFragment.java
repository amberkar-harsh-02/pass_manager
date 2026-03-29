package com.example.passmanager.ui.main;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.passmanager.R;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

public class AboutFragment extends Fragment {

    private int versionTapCount = 0;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_about, container, false);

        TextView textVersion = view.findViewById(R.id.text_version_info);
        LinearLayout rowFieldManual = view.findViewById(R.id.row_field_manual);
        LinearLayout rowRestartBridge = view.findViewById(R.id.row_restart_bridge);
        LinearLayout rowClearCache = view.findViewById(R.id.row_clear_cache);

        // --- 1. The Easter Egg ---
        textVersion.setOnClickListener(v -> {
            versionTapCount++;
            if (versionTapCount == 7) {
                Toast.makeText(requireContext(), "Developer Mode Unlocked. (Just kidding, stay secure!)", Toast.LENGTH_LONG).show();
                versionTapCount = 0; // Reset
            } else if (versionTapCount >= 3) {
                Toast.makeText(requireContext(), (7 - versionTapCount) + " taps away from being a developer...", Toast.LENGTH_SHORT).show();
            }
        });

        // --- 2. Field Manual & Privacy Briefing ---
        rowFieldManual.setOnClickListener(v -> {

            String briefingText = "Transparency and operational security are our top priorities, which is why this tool is built entirely on a strict zero-knowledge architecture. Every credential you save is encrypted locally on your device using AES-256-GCM before it ever touches the internal database. We cannot see your data, and any payloads fired to your computer transit via an ephemeral, unlogged WebSocket relay to ensure your operations remain completely dark.\n\n" +
                    "When you launch the app, your Threat Dashboard immediately goes to work auditing your vault. It actively scans your saved credentials and flags any password with a critical health score as a vulnerability, allowing you to tap and cycle compromised keys instantly.\n\n" +
                    "For new operations, the VaultShield Sign-Up Injector is your primary defense against clipboard loggers and keyloggers. By accessing the add menu, you can generate cryptographically secure passwords on the fly and use the built-in scanner to seamlessly inject them directly into your Ledger Browser Extension.\n\n" +
                    "Finally, to keep your digital footprint small, Ledger eliminates the need for third-party 2FA apps. You can link Base32 Secret Keys directly to your vault accounts, generating rolling six-digit TOTP codes natively within your secure environment.\n\n" +
                    "──────────────\n" +
                    "Ledger Security\n" +
                    "Engineered by Harsh Amberkar";

            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Operational Briefing")
                    .setMessage(briefingText)
                    .setPositiveButton("Understood", null)
                    .show();
        });

        // --- 3. Utilities ---
        rowRestartBridge.setOnClickListener(v -> {
            Toast.makeText(requireContext(), "WebSocket Listener connection forcefully reset.", Toast.LENGTH_SHORT).show();
        });

        rowClearCache.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Clear Cache")
                    .setMessage("This will clear temporary UI assets. Your encrypted vault data will NOT be deleted.")
                    .setPositiveButton("Clear", (dialog, which) -> {
                        requireContext().getCacheDir().delete();
                        Toast.makeText(requireContext(), "Local cache cleared.", Toast.LENGTH_SHORT).show();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        return view;
    }
}