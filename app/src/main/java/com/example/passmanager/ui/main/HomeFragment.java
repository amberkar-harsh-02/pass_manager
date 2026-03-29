package com.example.passmanager.ui.main;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.passmanager.R;
import com.example.passmanager.data.model.Credential;
import com.example.passmanager.ui.viewmodel.VaultViewModel;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

public class HomeFragment extends Fragment {

    private VaultViewModel vaultViewModel;
    private TextView textTotalPasswords;
    private TextView textWeakPasswords;
    private TextView textStrongPasswords;

    // Split the text views
    private TextView textHeaderGreeting;
    private TextView textQuip;

    private CredentialAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        textTotalPasswords = view.findViewById(R.id.text_total_passwords);
        textWeakPasswords = view.findViewById(R.id.text_weak_passwords);
        textStrongPasswords = view.findViewById(R.id.text_strong_passwords);

        // Link the new IDs
        textHeaderGreeting = view.findViewById(R.id.textHeaderGreeting);
        textQuip = view.findViewById(R.id.textQuip);

        // --- SETUP DYNAMIC GREETING ---
        setupStandUpGreeting();

        // --- Setup the Vulnerability List ---
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView_vulnerabilities);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CredentialAdapter();
        recyclerView.setAdapter(adapter);

        // --- CHECK THREAT LEVEL ---
        boolean isDuressMode = requireActivity().getIntent().getBooleanExtra("IS_DURESS_MODE", false);

        vaultViewModel = new ViewModelProvider(requireActivity()).get(VaultViewModel.class);

        vaultViewModel.getAllCredentials().observe(getViewLifecycleOwner(), credentials -> {
            if (isDuressMode) {
                // THE ILLUSION: Hardcode everything to zero and clear the list
                textTotalPasswords.setText("0");
                textWeakPasswords.setText("0");
                textStrongPasswords.setText("0");
                adapter.setCredentials(new ArrayList<>());
            } else if (credentials != null) {
                // REAL MODE: Calculate actual security scores
                int total = credentials.size();
                int weakCount = 0;
                int strongCount = 0;

                List<Credential> vulnerableList = new ArrayList<>();

                for (Credential cred : credentials) {
                    if (cred.getHealthScore() <= 1) {
                        weakCount++;
                        vulnerableList.add(cred);
                    } else {
                        strongCount++;
                    }
                }

                textTotalPasswords.setText(String.valueOf(total));
                textWeakPasswords.setText(String.valueOf(weakCount));
                textStrongPasswords.setText(String.valueOf(strongCount));

                adapter.setCredentials(vulnerableList);
            }
        });

        // Click to edit vulnerable passwords
        adapter.setOnItemClickListener(credential -> {
            android.content.Intent intent = new android.content.Intent(getContext(), EditCredentialActivity.class);
            intent.putExtra("CREDENTIAL_ID", credential.getId());
            intent.putExtra("CREDENTIAL_TITLE", credential.getTitle());
            intent.putExtra("CREDENTIAL_USERNAME", credential.getUsername());
            intent.putExtra("CREDENTIAL_ENCRYPTED_PW", credential.getEncryptedPassword());
            intent.putExtra("CREDENTIAL_IV", credential.getEncryptionIv());
            startActivity(intent);
        });

        return view;
    }

    private void setupStandUpGreeting() {
        if (textHeaderGreeting == null || textQuip == null) return;

        // FETCH THE NAME DYNAMICALLY! Fallback to "Agent" if not found.
        android.content.SharedPreferences prefs = requireContext().getSharedPreferences("VaultSecurityPrefs", android.content.Context.MODE_PRIVATE);
        String firstName = prefs.getString("USER_FIRST_NAME", "Agent");

        // 1. Get the time of day for the standard intro
        Calendar c = Calendar.getInstance();
        int timeOfDay = c.get(Calendar.HOUR_OF_DAY);
        String timeGreeting;

        if (timeOfDay >= 0 && timeOfDay < 12) {
            timeGreeting = "Good Morning, " + firstName;
        } else if (timeOfDay >= 12 && timeOfDay < 16) {
            timeGreeting = "Good Afternoon, " + firstName;
        } else if (timeOfDay >= 16 && timeOfDay < 21) {
            timeGreeting = "Good Evening, " + firstName;
        } else {
            timeGreeting = "Up late, " + firstName + "?";
        }

        // 2. The Arsenal of Material
        String[] securityQuips = {
                "Treat your passwords like your toothbrush: don't share them.",
                "'password123' is a punchline, not a key.",
                "My password is the last 8 digits of Pi. Good luck.",
                "Ah shit, here we go again... time to audit those credentials.",
                "Your password is like a joke. If you have to explain it, it's not strong enough.",
                "There are 10 types of people: those who understand binary, and those who get breached.",
                "Dance like nobody's watching. Encrypt like everyone is.",
                "Why did the hacker go broke? He couldn't find the cache.",
                "Passwords are like underwear: you don't let people see it, you should change it very often, and you shouldn't share it with strangers."
        };

        // 3. Pick a random quip
        Random random = new Random();
        int randomIndex = random.nextInt(securityQuips.length);
        String selectedQuip = securityQuips[randomIndex];

        // 4. Apply to the separate text views
        textHeaderGreeting.setText(timeGreeting);
        textQuip.setText(selectedQuip);
    }
}