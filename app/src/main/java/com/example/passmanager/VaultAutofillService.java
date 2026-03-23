package com.example.passmanager;

import android.app.assist.AssistStructure;
import android.service.autofill.AutofillService;
import android.service.autofill.FillCallback;
import android.service.autofill.FillContext;
import android.service.autofill.FillRequest;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveCallback;
import android.service.autofill.SaveInfo;
import android.service.autofill.SaveRequest;
import android.util.Log;
import android.view.autofill.AutofillId;
import android.os.CancellationSignal;

import com.example.passmanager.data.model.Credential;
import com.example.passmanager.repository.CredentialRepository;
import com.example.passmanager.security.EncryptionUtil;

import java.util.List;

public class VaultAutofillService extends AutofillService {

    private static final String TAG = "VaultAutofill";

    @Override
    public void onFillRequest(FillRequest request, CancellationSignal cancellationSignal, FillCallback callback) {
        Log.d(TAG, "onFillRequest: Scanning screen...");

        List<FillContext> contexts = request.getFillContexts();
        AssistStructure structure = contexts.get(contexts.size() - 1).getStructure();

        ParsedStructure parsed = new ParsedStructure();
        int nodes = structure.getWindowNodeCount();
        for (int i = 0; i < nodes; i++) {
            scanForAutofillIds(structure.getWindowNodeAt(i).getRootViewNode(), parsed);
        }

        if (parsed.usernameId != null || parsed.passwordId != null) {

            // 1. Setup the Save Interceptor
            int flags = 0;
            AutofillId[] idsToWatch;

            if (parsed.passwordId == null && parsed.usernameId != null) {
                flags = SaveInfo.FLAG_DELAY_SAVE;
                idsToWatch = new AutofillId[]{parsed.usernameId};
            } else if (parsed.passwordId != null && parsed.usernameId != null) {
                idsToWatch = new AutofillId[]{parsed.usernameId, parsed.passwordId};
            } else {
                idsToWatch = new AutofillId[]{parsed.passwordId};
            }

            SaveInfo saveInfo = new SaveInfo.Builder(
                    SaveInfo.SAVE_DATA_TYPE_USERNAME | SaveInfo.SAVE_DATA_TYPE_PASSWORD,
                    idsToWatch
            ).setFlags(flags).build();

            // 2. Determine the Target Title to search for
            android.content.ComponentName component = structure.getActivityComponent();
            String rawPackageName = (component != null) ? component.getPackageName() : "Unknown App";
            String searchTitle = rawPackageName;

            if (parsed.webDomain != null && !parsed.webDomain.isEmpty()) {
                searchTitle = parsed.webDomain;
            } else {
                try {
                    android.content.pm.PackageManager pm = getApplicationContext().getPackageManager();
                    android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(rawPackageName, 0);
                    searchTitle = (String) pm.getApplicationLabel(ai);
                } catch (Exception e) {
                    Log.w(TAG, "Could not find app name.");
                }
            }

            final String finalSearchTitle = searchTitle;

            // 3. Query the Database on a Background Thread
            java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
                FillResponse.Builder responseBuilder = new FillResponse.Builder().setSaveInfo(saveInfo);

                try {
                    CredentialRepository repo = new CredentialRepository(getApplication());
                    List<Credential> allCreds = repo.getAllCredentialsSync();

                    boolean foundMatch = false;

                    for (Credential cred : allCreds) {
                        // Look for a match
                        if (cred.getTitle().toLowerCase().contains(finalSearchTitle.toLowerCase()) ||
                                finalSearchTitle.toLowerCase().contains(cred.getTitle().toLowerCase())) {

                            foundMatch = true;
                            String decryptedPassword = EncryptionUtil.decryptPassword(cred.getEncryptedPassword(), cred.getEncryptionIv());

                            String displayUser = cred.getUsername();
                            if (displayUser == null || displayUser.trim().isEmpty()) {
                                displayUser = "Saved Password";
                            }

                            // Build the UI Dropdown Row (Using the custom Ledger layout)
                            android.widget.RemoteViews presentation = new android.widget.RemoteViews(getPackageName(), R.layout.autofill_dropdown_item);
                            presentation.setTextViewText(R.id.autofill_text, displayUser);

                            // Attach the data to the boxes
                            android.service.autofill.Dataset.Builder datasetBuilder = new android.service.autofill.Dataset.Builder();

                            if (parsed.usernameId != null) {
                                String safeUser = cred.getUsername() != null ? cred.getUsername() : "";
                                datasetBuilder.setValue(parsed.usernameId, android.view.autofill.AutofillValue.forText(safeUser), presentation);
                            }
                            if (parsed.passwordId != null) {
                                datasetBuilder.setValue(parsed.passwordId, android.view.autofill.AutofillValue.forText(decryptedPassword), presentation);
                            }

                            responseBuilder.addDataset(datasetBuilder.build());
                        }
                    }

                    if (foundMatch) {
                        Log.d(TAG, "Found matches! Injecting datasets.");
                    } else {
                        Log.d(TAG, "No matches found. Injecting 'Search Ledger' fallback.");

                        android.content.Intent authIntent = new android.content.Intent(getApplicationContext(), AutofillPickerActivity.class);
                        if (parsed.usernameId != null) authIntent.putExtra("target_username_id", parsed.usernameId);
                        if (parsed.passwordId != null) authIntent.putExtra("target_password_id", parsed.passwordId);

                        android.app.PendingIntent pendingIntent = android.app.PendingIntent.getActivity(
                                getApplicationContext(),
                                1001,
                                authIntent,
                                android.app.PendingIntent.FLAG_CANCEL_CURRENT | android.app.PendingIntent.FLAG_MUTABLE
                        );
                        android.content.IntentSender intentSender = pendingIntent.getIntentSender();

                        // Build the Custom Ledger UI for the fallback button
                        android.widget.RemoteViews authPresentation = new android.widget.RemoteViews(getPackageName(), R.layout.autofill_dropdown_item);
                        authPresentation.setTextViewText(R.id.autofill_text, "Search Ledger...");

                        // THE FIX: Attach Intent directly to the FillResponse to wipe out the "Double Tap" bug
                        responseBuilder.setAuthentication(idsToWatch, intentSender, authPresentation);
                    }

                } catch (Exception e) {
                    Log.e(TAG, "Error building autofill datasets", e);
                }

                // 4. Send the payload back to the Android OS
                callback.onSuccess(responseBuilder.build());
            });

        } else {
            callback.onSuccess(null);
        }
    }

    @Override
    public void onSaveRequest(SaveRequest request, SaveCallback callback) {
        Log.d(TAG, "onSaveRequest: User clicked Save! Ripping data...");

        List<FillContext> contexts = request.getFillContexts();
        ParsedStructure finalData = new ParsedStructure();

        for (FillContext context : contexts) {
            AssistStructure structure = context.getStructure();
            for (int i = 0; i < structure.getWindowNodeCount(); i++) {
                scanForData(structure.getWindowNodeAt(i).getRootViewNode(), finalData);
            }
        }

        // --- THE VAULT DROP ---
        Log.d(TAG, "Rip Complete. Username: " + (finalData.usernameText != null ? finalData.usernameText : "NULL"));
        Log.d(TAG, "Rip Complete. Password: " + (finalData.passwordText != null ? "FOUND (Hidden)" : "NULL"));

        if (finalData.passwordText != null) {
            String rawPackageName = contexts.get(contexts.size() - 1).getStructure().getActivityComponent().getPackageName();
            String finalTitle = rawPackageName;

            if (finalData.webDomain != null && !finalData.webDomain.isEmpty()) {
                finalTitle = DomainFormatter.formatWebsiteName(finalData.webDomain);
            } else {
                try {
                    android.content.pm.PackageManager pm = getApplicationContext().getPackageManager();
                    android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(rawPackageName, 0);
                    finalTitle = (String) pm.getApplicationLabel(ai);
                } catch (Exception e) {
                    Log.w(TAG, "Could not find app name.");
                }
            }

            final String finalDbTitle = finalTitle;
            String user = finalData.usernameText != null ? finalData.usernameText.toString() : "Unknown User";
            String pass = finalData.passwordText.toString();

            java.util.concurrent.Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    android.util.Pair<String, String> encryptedData = EncryptionUtil.encryptPassword(pass);
                    Credential newAccount = new Credential(finalDbTitle, user, encryptedData.first, encryptedData.second, 1, null);

                    CredentialRepository repo = new CredentialRepository(getApplication());
                    repo.insert(newAccount);

                    android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
                    handler.post(() -> android.widget.Toast.makeText(getApplicationContext(),
                            "Ledger: Saved " + finalDbTitle + " to Vault!",
                            android.widget.Toast.LENGTH_LONG).show());

                } catch (Exception e) {
                    Log.e(TAG, "Failed to encrypt/save to Vault", e);
                }
            });
        } else {
            Log.e(TAG, "ABORTING SAVE: Password text was null! The scanner couldn't rip the text.");
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            handler.post(() -> android.widget.Toast.makeText(getApplicationContext(),
                    "Ledger Error: Could not read the password off the screen.",
                    android.widget.Toast.LENGTH_LONG).show());
        }
        callback.onSuccess();
    }

    // --- THE SCREEN PARSER HELPERS ---
    private static class ParsedStructure {
        AutofillId usernameId;
        AutofillId passwordId;
        CharSequence usernameText;
        CharSequence passwordText;
        String webDomain;
    }

    // Pass 1: Finding the boxes on load
    private void scanForAutofillIds(AssistStructure.ViewNode node, ParsedStructure parsed) {
        if (node.getAutofillHints() != null) {
            for (String hint : node.getAutofillHints()) {
                String h = hint.toLowerCase();
                if (h.contains("username") || h.contains("email")) {
                    parsed.usernameId = node.getAutofillId();
                } else if (h.contains("password")) {
                    parsed.passwordId = node.getAutofillId();
                }
            }
        }

        if (node.getAutofillId() != null) {
            String viewHint = node.getHint() != null ? node.getHint().toString().toLowerCase() : "";
            String viewId = node.getIdEntry() != null ? node.getIdEntry().toLowerCase() : "";

            if (parsed.usernameId == null && (viewHint.contains("email") || viewHint.contains("user") || viewId.contains("email") || viewId.contains("user"))) {
                parsed.usernameId = node.getAutofillId();
            }
            if (parsed.passwordId == null && (viewHint.contains("password") || viewHint.contains("pass") || viewId.contains("password") || viewId.contains("pass"))) {
                parsed.passwordId = node.getAutofillId();
            }
        }

        android.view.ViewStructure.HtmlInfo htmlInfo = node.getHtmlInfo();
        if (htmlInfo != null && "input".equalsIgnoreCase(htmlInfo.getTag())) {
            for (android.util.Pair<String, String> attr : htmlInfo.getAttributes()) {
                String attrName = attr.first != null ? attr.first.toLowerCase() : "";
                String attrValue = attr.second != null ? attr.second.toLowerCase() : "";

                if (parsed.usernameId == null && (attrValue.contains("email") || attrValue.contains("username") || attrValue.contains("login"))) {
                    parsed.usernameId = node.getAutofillId();
                }
                if (parsed.passwordId == null && (attrValue.contains("password") || attrValue.contains("pass") || (attrName.equals("type") && attrValue.equals("password")))) {
                    parsed.passwordId = node.getAutofillId();
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            scanForAutofillIds(node.getChildAt(i), parsed);
        }
    }

    // Pass 2: Extracting the typed text on save
    private void scanForData(android.app.assist.AssistStructure.ViewNode node, ParsedStructure parsed) {
        if (node.getWebDomain() != null) {
            parsed.webDomain = node.getWebDomain();
        }

        boolean hasActualText = node.getText() != null && node.getText().toString().trim().length() > 0;

        boolean isPasswordNode = false;
        if (node.getInputType() != 0) {
            int variation = node.getInputType() & android.text.InputType.TYPE_MASK_VARIATION;
            if (variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD) {
                isPasswordNode = true;
            }
        }

        if (hasActualText && isPasswordNode) {
            parsed.passwordText = node.getText();
        }

        if (node.getAutofillHints() != null && hasActualText) {
            for (String hint : node.getAutofillHints()) {
                String h = hint.toLowerCase();
                if (h.contains("username") || h.contains("email")) parsed.usernameText = node.getText();
                else if (h.contains("password")) parsed.passwordText = node.getText();
            }
        }

        if (hasActualText) {
            String viewId = node.getIdEntry() != null ? node.getIdEntry().toLowerCase() : "";

            if (parsed.usernameText == null && (viewId.contains("email") || viewId.contains("user") || viewId.contains("login"))) {
                parsed.usernameText = node.getText();
            }
            if (parsed.passwordText == null && (viewId.contains("password") || viewId.contains("pass"))) {
                parsed.passwordText = node.getText();
            }
        }

        android.view.ViewStructure.HtmlInfo htmlInfo = node.getHtmlInfo();
        if (htmlInfo != null && "input".equalsIgnoreCase(htmlInfo.getTag()) && hasActualText) {
            for (android.util.Pair<String, String> attr : htmlInfo.getAttributes()) {
                String attrName = attr.first != null ? attr.first.toLowerCase() : "";
                String attrValue = attr.second != null ? attr.second.toLowerCase() : "";

                if (parsed.usernameText == null && (attrValue.contains("email") || attrValue.contains("username") || attrValue.contains("login"))) {
                    parsed.usernameText = node.getText();
                }
                if (parsed.passwordText == null && (attrValue.contains("password") || attrValue.contains("pass") || (attrName.equals("type") && attrValue.equals("password")))) {
                    parsed.passwordText = node.getText();
                }
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            scanForData(node.getChildAt(i), parsed);
        }
    }
}