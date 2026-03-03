package com.example.passmanager.ui.main;

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
import java.util.List;

public class HomeFragment extends Fragment {

    private VaultViewModel vaultViewModel;
    private TextView textTotalPasswords;
    private TextView textWeakPasswords;
    private TextView textStrongPasswords;
    private CredentialAdapter adapter; // Reusing your adapter!

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        textTotalPasswords = view.findViewById(R.id.text_total_passwords);
        textWeakPasswords = view.findViewById(R.id.text_weak_passwords);
        textStrongPasswords = view.findViewById(R.id.text_strong_passwords);

        // --- NEW: Setup the Vulnerability List ---
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView_vulnerabilities);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CredentialAdapter();
        recyclerView.setAdapter(adapter);

        vaultViewModel = new ViewModelProvider(requireActivity()).get(VaultViewModel.class);

        vaultViewModel.getAllCredentials().observe(getViewLifecycleOwner(), credentials -> {
            if (credentials != null) {
                int total = credentials.size();
                int weakCount = 0;
                int strongCount = 0;

                // This list will hold ONLY the weak passwords
                List<Credential> vulnerableList = new ArrayList<>();

                for (Credential cred : credentials) {
                    if (cred.getHealthScore() <= 1) {
                        weakCount++;
                        vulnerableList.add(cred); // Add to the threat list
                    } else {
                        strongCount++;
                    }
                }

                textTotalPasswords.setText(String.valueOf(total));
                textWeakPasswords.setText(String.valueOf(weakCount));
                textStrongPasswords.setText(String.valueOf(strongCount));

                // Push only the weak passwords to the screen
                adapter.setCredentials(vulnerableList);
            }
        });

        // For now, clicking an item just tells the user they need to edit it
        adapter.setOnItemClickListener(credential -> {
            android.content.Intent intent = new android.content.Intent(getContext(), EditCredentialActivity.class);

            // Package up the old data to send to the Edit Screen
            intent.putExtra("CREDENTIAL_ID", credential.getId());
            intent.putExtra("CREDENTIAL_TITLE", credential.getTitle());
            intent.putExtra("CREDENTIAL_USERNAME", credential.getUsername());
            intent.putExtra("CREDENTIAL_ENCRYPTED_PW", credential.getEncryptedPassword());
            intent.putExtra("CREDENTIAL_IV", credential.getEncryptionIv());

            startActivity(intent);
        });

        return view;
    }
}