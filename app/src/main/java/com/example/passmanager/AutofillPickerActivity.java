package com.example.passmanager;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.passmanager.repository.CredentialRepository;
import com.example.passmanager.data.model.Credential;
import com.example.passmanager.security.EncryptionUtil;
import java.util.List;

public class AutofillPickerActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_autofill_picker);

        // 1. Catch the target coordinates
        android.view.autofill.AutofillId usernameId = getIntent().getParcelableExtra("target_username_id");
        android.view.autofill.AutofillId passwordId = getIntent().getParcelableExtra("target_password_id");

        ListView listView = findViewById(R.id.pickerListView);

        // 2. Query the entire vault on a background thread
        java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
            try {
                CredentialRepository repo = new CredentialRepository(getApplication());
                List<Credential> allCreds = repo.getAllCredentialsSync();

                // 3. Push data to the UI thread with a CUSTOM PREMIUM ADAPTER
                runOnUiThread(() -> {
                    ArrayAdapter<Credential> adapter = new ArrayAdapter<Credential>(this, R.layout.item_picker_credential, allCreds) {
                        @Override
                        public android.view.View getView(int position, android.view.View convertView, android.view.ViewGroup parent) {
                            if (convertView == null) {
                                convertView = getLayoutInflater().inflate(R.layout.item_picker_credential, parent, false);
                            }
                            Credential cred = getItem(position);
                            android.widget.TextView title = convertView.findViewById(R.id.text_title);
                            android.widget.TextView username = convertView.findViewById(R.id.text_username);

                            title.setText(cred.getTitle());
                            String displayUser = cred.getUsername();
                            // Aesthetic: Cleanly show if username is empty
                            username.setText((displayUser != null && !displayUser.trim().isEmpty()) ? displayUser : "Saved Password");
                            return convertView;
                        }
                    };
                    listView.setAdapter(adapter);

                    // 4. THE INJECTION TRIGGER: Fixes the double-tap bug
                    listView.setOnItemClickListener((parent, view, position, id) -> {
                        Credential selectedCred = allCreds.get(position);

                        try {
                            // Decrypt the payload
                            String decryptedPassword = EncryptionUtil.decryptPassword(selectedCred.getEncryptedPassword(), selectedCred.getEncryptionIv());

                            // A. Create Presentation for the main app UI
                            android.widget.RemoteViews presentation = new android.widget.RemoteViews(getPackageName(), R.layout.autofill_dropdown_item);
                            presentation.setTextViewText(R.id.autofill_text, selectedCred.getUsername());

                            // B. Build the Dataset (The actual data package)
                            android.service.autofill.Dataset.Builder datasetBuilder = new android.service.autofill.Dataset.Builder();

                            if (usernameId != null) datasetBuilder.setValue(usernameId, android.view.autofill.AutofillValue.forText(selectedCred.getUsername()), presentation);
                            if (passwordId != null) datasetBuilder.setValue(passwordId, android.view.autofill.AutofillValue.forText(decryptedPassword), presentation);

                            // C. FIX: Wrap the Dataset inside a FillResponse so it overwrites both fields simultaneously.
                            android.service.autofill.FillResponse masterResponse = new android.service.autofill.FillResponse.Builder()
                                    .addDataset(datasetBuilder.build())
                                    .build();

                            // D. Package the FillResponse and send it back to Android OS
                            android.content.Intent replyIntent = new android.content.Intent();
                            replyIntent.putExtra(android.view.autofill.AutofillManager.EXTRA_AUTHENTICATION_RESULT, masterResponse);
                            setResult(RESULT_OK, replyIntent);

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