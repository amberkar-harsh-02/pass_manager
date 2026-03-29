package com.example.passmanager.ui.main;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.passmanager.R;
import com.example.passmanager.data.model.Credential;
import com.example.passmanager.ui.viewmodel.VaultViewModel;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.List;

public class AuthenticatorFragment extends Fragment {

    private VaultViewModel vaultViewModel;
    private AuthenticatorAdapter adapter;
    private TextView textEmpty;
    private List<Credential> allVaultCredentials = new ArrayList<>();

    // The Master Clock
    private final Handler clockHandler = new Handler(Looper.getMainLooper());
    private final Runnable clockRunnable = new Runnable() {
        @Override
        public void run() {
            if (adapter != null && adapter.getItemCount() > 0) {
                // Send a "TICK" payload so it updates the rings smoothly without jittering the list
                adapter.notifyItemRangeChanged(0, adapter.getItemCount(), "TICK");
            }
            // Pulse again in exactly 1 second
            clockHandler.postDelayed(this, 1000);
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_authenticator, container, false);

        textEmpty = view.findViewById(R.id.text_empty_auth);
        RecyclerView recyclerView = view.findViewById(R.id.recycler_authenticator);
        FloatingActionButton fabAdd = view.findViewById(R.id.fab_add_auth);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));

        adapter = new AuthenticatorAdapter(requireContext());
        recyclerView.setAdapter(adapter);

        // --- DURESS MODE CHECK ---
        boolean isDuressMode = requireActivity().getIntent().getBooleanExtra("IS_DURESS_MODE", false);

        // If in Duress Mode, hide the ability to add new keys
        if (isDuressMode) {
            fabAdd.setVisibility(View.GONE);
        }

        vaultViewModel = new ViewModelProvider(requireActivity()).get(VaultViewModel.class);

        // 1. Observe the Vault and Filter for 2FA Codes
        vaultViewModel.getAllCredentials().observe(getViewLifecycleOwner(), credentials -> {

            // THE DURESS INTERCEPTOR: If compromised, force the list to be empty and break out early
            if (isDuressMode) {
                adapter.setCredentials(new ArrayList<>());
                textEmpty.setVisibility(View.VISIBLE);
                textEmpty.setText("No 2FA Codes Configured.\n\nYour vault is currently empty."); // Slightly stealthier text
                return;
            }

            // STANDARD OPERATION: Load the real keys
            if (credentials != null) {
                allVaultCredentials = credentials; // Keep a copy of everything for the "Add" menu

                List<Credential> authList = new ArrayList<>();
                for (Credential cred : credentials) {
                    if (cred.getTotpSecret() != null && !cred.getTotpSecret().trim().isEmpty()) {
                        authList.add(cred);
                    }
                }

                adapter.setCredentials(authList);
                textEmpty.setVisibility(authList.isEmpty() ? View.VISIBLE : View.GONE);

                // Ensure default text is set if not under duress
                if (authList.isEmpty()) {
                    textEmpty.setText("No 2FA Codes Configured\n\nTap + to link an account.");
                }
            }
        });

        // 2. The Link Button
        fabAdd.setOnClickListener(v -> showLinkAuthenticatorDialog());

        return view;
    }

    private void showLinkAuthenticatorDialog() {
        // Find accounts that DON'T have a 2FA code yet
        List<Credential> eligibleAccounts = new ArrayList<>();
        List<String> accountNames = new ArrayList<>();

        for (Credential c : allVaultCredentials) {
            if (c.getTotpSecret() == null || c.getTotpSecret().trim().isEmpty()) {
                eligibleAccounts.add(c);
                accountNames.add(c.getTitle() + " (" + c.getUsername() + ")");
            }
        }

        if (eligibleAccounts.isEmpty()) {
            Toast.makeText(getContext(), "All your vault accounts already have 2FA linked, or your vault is empty!", Toast.LENGTH_LONG).show();
            return;
        }

        // Build a custom view for the Dialog
        android.widget.LinearLayout layout = new android.widget.LinearLayout(requireContext());
        layout.setOrientation(android.widget.LinearLayout.VERTICAL);
        layout.setPadding(50, 40, 50, 10);

        Spinner spinner = new Spinner(requireContext());
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_dropdown_item, accountNames);
        spinner.setAdapter(spinnerAdapter);
        layout.addView(spinner);

        EditText secretInput = new EditText(requireContext());
        secretInput.setHint("Paste Base32 Secret Key");
        secretInput.setSingleLine();
        layout.addView(secretInput);

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Link Authenticator")
                .setMessage("Select an account from your vault and paste the secret key provided by the website.")
                .setView(layout)
                .setPositiveButton("Link to Vault", (dialog, which) -> {
                    String secret = secretInput.getText().toString().trim();
                    if (secret.isEmpty()) {
                        Toast.makeText(getContext(), "Secret key cannot be empty", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Grab the selected account and update it!
                    int selectedIndex = spinner.getSelectedItemPosition();
                    Credential targetCred = eligibleAccounts.get(selectedIndex);
                    targetCred.setTotpSecret(secret);

                    vaultViewModel.update(targetCred);
                    Toast.makeText(getContext(), "Authenticator Linked!", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // --- MANAGE THE CLOCK LIFECYCLE ---
    // We only want the clock ticking if the user is actually looking at the tab!
    @Override
    public void onResume() {
        super.onResume();
        clockHandler.post(clockRunnable);
    }

    @Override
    public void onPause() {
        super.onPause();
        clockHandler.removeCallbacks(clockRunnable);
    }
}