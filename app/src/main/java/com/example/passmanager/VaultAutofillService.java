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

            // 1. Setup the Save Interceptor (Just like before)
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

            // 2. Determine the Target Title to search for (Website Domain or App Name)
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
                        // Look for a match (e.g., checking if "madhat.io" matches the DB title)
                        if (cred.getTitle().toLowerCase().contains(finalSearchTitle.toLowerCase()) ||
                                finalSearchTitle.toLowerCase().contains(cred.getTitle().toLowerCase())) {

                            foundMatch = true;

                            // Decrypt the payload
                            String decryptedPassword = EncryptionUtil.decryptPassword(cred.getEncryptedPassword(), cred.getEncryptionIv());

                            // Build the UI Dropdown Row
                            android.widget.RemoteViews presentation = new android.widget.RemoteViews(getPackageName(), android.R.layout.simple_list_item_1);
                            presentation.setTextViewText(android.R.id.text1, "🛡️ " + cred.getUsername());

                            // Attach the decrypted data to the boxes
                            android.service.autofill.Dataset.Builder datasetBuilder = new android.service.autofill.Dataset.Builder();

                            if (parsed.usernameId != null) {
                                datasetBuilder.setValue(parsed.usernameId, android.view.autofill.AutofillValue.forText(cred.getUsername()), presentation);
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
                        Log.d(TAG, "No matches found in Vault for: " + finalSearchTitle);
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

        // Android gives us the HISTORY of all screens in this login session
        List<FillContext> contexts = request.getFillContexts();
        ParsedStructure finalData = new ParsedStructure();

        // Iterate through history to piece together the Username and Password
        for (FillContext context : contexts) {
            AssistStructure structure = context.getStructure();
            for (int i = 0; i < structure.getWindowNodeCount(); i++) {
                scanForData(structure.getWindowNodeAt(i).getRootViewNode(), finalData);
            }
        }

        // --- THE VAULT DROP ---

        // DIAGNOSTIC LOGS: Let's see exactly what the scanner found!
        Log.d(TAG, "Rip Complete. Username: " + (finalData.usernameText != null ? finalData.usernameText : "NULL"));
        Log.d(TAG, "Rip Complete. Password: " + (finalData.passwordText != null ? "FOUND (Hidden)" : "NULL"));

        if (finalData.passwordText != null) {
            String rawPackageName = contexts.get(contexts.size() - 1).getStructure().getActivityComponent().getPackageName();
            String finalTitle = rawPackageName; // Default fallback

            // 1. Determine the best readable Title
            if (finalData.webDomain != null && !finalData.webDomain.isEmpty()) {
                finalTitle = finalData.webDomain;
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
                    Credential newAccount = new Credential(finalDbTitle, user, encryptedData.first, encryptedData.second, 1);

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
            // NEW: If it fails, scream about it!
            Log.e(TAG, "ABORTING SAVE: Password text was null! The scanner couldn't rip the text.");
            android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            handler.post(() -> android.widget.Toast.makeText(getApplicationContext(),
                    "Ledger Error: Could not read the password off the screen.",
                    android.widget.Toast.LENGTH_LONG).show());
        }
        callback.onSuccess();
    }

    // --- THE SCREEN PARSER HELPERS ---
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

        // 1. Check Official Autofill Hints (The Polite Way)
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

        // 2. Android Native Heuristics (Apps)
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

        // 3. WEB BROWSER HEURISTICS (Chrome / WebViews)
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

        // 4. Drill down into all child elements on the screen
        for (int i = 0; i < node.getChildCount(); i++) {
            scanForAutofillIds(node.getChildAt(i), parsed);
        }
    }

    // Pass 2: Extracting the typed text on save (Bulletproof Version)
    // Pass 2: Extracting the typed text on save (Corrected Silver Bullet)
    private void scanForData(android.app.assist.AssistStructure.ViewNode node, ParsedStructure parsed) {
        if (node.getWebDomain() != null) {
            parsed.webDomain = node.getWebDomain();
        }

        boolean hasActualText = node.getText() != null && node.getText().toString().trim().length() > 0;

        // 1. THE SILVER BULLET: Check the raw InputType for password flags
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

        // 2. Official Hints
        if (node.getAutofillHints() != null && hasActualText) {
            for (String hint : node.getAutofillHints()) {
                String h = hint.toLowerCase();
                if (h.contains("username") || h.contains("email")) parsed.usernameText = node.getText();
                else if (h.contains("password")) parsed.passwordText = node.getText();
            }
        }

        // 3. Fallback: ID Matching
        if (hasActualText) {
            String viewId = node.getIdEntry() != null ? node.getIdEntry().toLowerCase() : "";

            if (parsed.usernameText == null && (viewId.contains("email") || viewId.contains("user") || viewId.contains("login"))) {
                parsed.usernameText = node.getText();
            }
            if (parsed.passwordText == null && (viewId.contains("password") || viewId.contains("pass"))) {
                parsed.passwordText = node.getText();
            }
        }

        // 4. Web Browser Data Extraction (Chrome WebViews)
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

        // 5. Drill down into child elements
        for (int i = 0; i < node.getChildCount(); i++) {
            scanForData(node.getChildAt(i), parsed);
        }
    }
}