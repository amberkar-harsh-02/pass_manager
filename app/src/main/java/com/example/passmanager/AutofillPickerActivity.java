package com.example.passmanager;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.passmanager.data.model.Credential;
import com.example.passmanager.repository.CredentialRepository;
import com.example.passmanager.security.EncryptionUtil;
import java.util.ArrayList;
import java.util.List;

public class AutofillPickerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_autofill_picker);

        // 1. Catch the target coordinates from the Service
        android.view.autofill.AutofillId usernameId = getIntent().getParcelableExtra("target_username_id");
        android.view.autofill.AutofillId passwordId = getIntent().getParcelableExtra("target_password_id");

        ListView listView = findViewById(R.id.pickerListView);

        // 2. Query the entire vault on a background thread
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try {
                CredentialRepository repo = new CredentialRepository(getApplication());
                List<Credential> allCreds = repo.getAllCredentialsSync();

                // Format strings for the ListView (e.g., "Google - harsh@email.com")
                List<String> displayList = new ArrayList<>();
                for (Credential c : allCreds) {
                    displayList.add(c.getTitle() + " - " + (c.getUsername() != null ? c.getUsername() : "Saved Password"));
                }

                // 3. Push data to the UI thread
                runOnUiThread(() -> {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, displayList);
                    listView.setAdapter(adapter);

                    // 4. THE INJECTION TRIGGER: When the user taps an account
                    listView.setOnItemClickListener((parent, view, position, id) -> {
                        Credential selectedCred = allCreds.get(position);

                        try {
                            // Decrypt the payload
                            String decryptedPassword = EncryptionUtil.decryptPassword(selectedCred.getEncryptedPassword(), selectedCred.getEncryptionIv());

                            // Build the final dataset
                            android.widget.RemoteViews presentation = new android.widget.RemoteViews(getPackageName(), android.R.layout.simple_list_item_1);
                            presentation.setTextViewText(android.R.id.text1, "🛡️ " + selectedCred.getUsername());

                            android.service.autofill.Dataset.Builder datasetBuilder = new android.service.autofill.Dataset.Builder();

                            if (usernameId != null) {
                                datasetBuilder.setValue(usernameId, android.view.autofill.AutofillValue.forText(selectedCred.getUsername()), presentation);
                            }
                            if (passwordId != null) {
                                datasetBuilder.setValue(passwordId, android.view.autofill.AutofillValue.forText(decryptedPassword), presentation);
                            }

                            // Package the dataset and send it back to Android OS
                            android.content.Intent replyIntent = new android.content.Intent();
                            replyIntent.putExtra(android.view.autofill.AutofillManager.EXTRA_AUTHENTICATION_RESULT, datasetBuilder.build());
                            setResult(RESULT_OK, replyIntent);

                            // Close the floating window!
                            finish();

                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }
}