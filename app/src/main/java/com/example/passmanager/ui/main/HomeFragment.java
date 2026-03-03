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

import com.example.passmanager.R;
import com.example.passmanager.ui.viewmodel.VaultViewModel;

public class HomeFragment extends Fragment {

    private VaultViewModel vaultViewModel;
    private TextView textTotalPasswords;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_home, container, false);

        // 1. Link the UI element
        textTotalPasswords = view.findViewById(R.id.text_total_passwords);

        // 2. Connect to the Main Activity's ViewModel (Database)
        // Note: We use 'requireActivity()' so it shares the exact same database instance as MainActivity
        vaultViewModel = new ViewModelProvider(requireActivity()).get(VaultViewModel.class);

        // 3. Observe the database and update the dashboard live
        vaultViewModel.getAllCredentials().observe(getViewLifecycleOwner(), credentials -> {
            if (credentials != null) {
                // Count the list size and update the giant number on the screen
                textTotalPasswords.setText(String.valueOf(credentials.size()));
            }
        });

        return view;
    }
}