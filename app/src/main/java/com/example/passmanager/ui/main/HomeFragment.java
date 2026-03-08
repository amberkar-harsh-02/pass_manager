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
import java.util.List;

public class HomeFragment extends Fragment {

    private VaultViewModel vaultViewModel;
    private TextView textTotalPasswords;
    private TextView textWeakPasswords;
    private TextView textStrongPasswords;
    private CredentialAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        textTotalPasswords = view.findViewById(R.id.text_total_passwords);
        textWeakPasswords = view.findViewById(R.id.text_weak_passwords);
        textStrongPasswords = view.findViewById(R.id.text_strong_passwords);

        // --- Setup the Vulnerability List ---
        RecyclerView recyclerView = view.findViewById(R.id.recyclerView_vulnerabilities);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new CredentialAdapter();
        recyclerView.setAdapter(adapter);

        // --- CHECK THREAT LEVEL ---
        boolean isDuressMode = requireActivity().getSharedPreferences("VaultSecurityPrefs", Context.MODE_PRIVATE)
                .getBoolean("IS_DURESS_MODE", false);

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
}