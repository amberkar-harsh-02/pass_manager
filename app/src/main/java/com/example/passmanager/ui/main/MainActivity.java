package com.example.passmanager.ui.main;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import android.widget.Toast;
import com.example.passmanager.security.EncryptionUtil;
import android.os.Bundle;
import android.view.WindowManager;
import com.example.passmanager.data.model.Credential;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.passmanager.R;
import com.example.passmanager.ui.viewmodel.VaultViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {

    private VaultViewModel vaultViewModel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // SECURITY: Block screenshots, screen recording, and recent-app previews
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.activity_main);

        RecyclerView recyclerView = findViewById(R.id.recyclerView_credentials);
        FloatingActionButton fabAdd = findViewById(R.id.fab_add_password);

        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setHasFixedSize(true);

        // Initialize the Adapter and attach it to the RecyclerView
        final CredentialAdapter adapter = new CredentialAdapter();
        recyclerView.setAdapter(adapter);

        // Initialize ViewModel
        vaultViewModel = new ViewModelProvider(this).get(VaultViewModel.class);

        // OBSERVE THE DATABASE:
        // Any time a password is added, updated, or deleted in the background,
        // this observer triggers and pushes the fresh list to the UI adapter.
        vaultViewModel.getAllCredentials().observe(this, (java.util.List<com.example.passmanager.data.model.Credential> credentials) -> {
            adapter.setCredentials(credentials);
        });
        adapter.setOnItemClickListener(credential -> {
            try {
                // 1. Decrypt the password using the ciphertext and IV
                String decryptedPassword = EncryptionUtil.decryptPassword(
                        credential.getEncryptedPassword(),
                        credential.getEncryptionIv()
                );

                // 2. Show the result in a secure pop-up dialog
                new MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle(credential.getTitle())
                        .setMessage("Username: " + credential.getUsername() + "\n\nPassword: " + decryptedPassword)
                        .setPositiveButton("Close", (dialog, which) -> dialog.dismiss())
                        .show();

            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(MainActivity.this, "Decryption Failed!", Toast.LENGTH_SHORT).show();
            }
        });
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder target) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // 1. Find out which item was swiped
                int position = viewHolder.getAdapterPosition();
                Credential credentialToDelete = adapter.getCredentialAt(position);

                // 2. Show confirmation dialog
                new MaterialAlertDialogBuilder(MainActivity.this)
                        .setTitle("Delete Password?")
                        .setMessage("Are you sure you want to delete the credentials for " + credentialToDelete.getTitle() + "?")
                        .setPositiveButton("Delete", (dialog, which) -> {
                            // User confirmed: Delete from database
                            vaultViewModel.delete(credentialToDelete);
                            Toast.makeText(MainActivity.this, "Credential Deleted", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> {
                            // User canceled: Undo the swipe animation and redraw the item
                            adapter.notifyItemChanged(position);
                            dialog.dismiss();
                        })
                        .setCancelable(false) // Forces the user to explicitly tap a button
                        .show();
            }
        }).attachToRecyclerView(recyclerView);

        // Setup FAB click listener
        fabAdd.setOnClickListener(v -> {
            // Open the screen to add a new password
            android.content.Intent intent = new android.content.Intent(MainActivity.this, AddCredentialActivity.class);
            startActivity(intent);
        });
    }
}