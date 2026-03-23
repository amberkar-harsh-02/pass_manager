package com.example.passmanager.ui.main;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.passmanager.R;
import com.example.passmanager.data.model.Credential;
import com.example.passmanager.security.TotpEngine;
import com.google.android.material.progressindicator.CircularProgressIndicator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AuthenticatorAdapter extends RecyclerView.Adapter<AuthenticatorAdapter.AuthViewHolder> {

    private List<Credential> credentials = new ArrayList<>();
    private final Set<Integer> revealedItemIds = new HashSet<>();
    private final Context context;

    public AuthenticatorAdapter(Context context) {
        this.context = context;
    }

    public void setCredentials(List<Credential> credentials) {
        this.credentials = credentials;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public AuthViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_authenticator, parent, false);
        return new AuthViewHolder(itemView);
    }

    // This is the "Full Rebind" (Runs when you scroll)
    @Override
    public void onBindViewHolder(@NonNull AuthViewHolder holder, int position) {
        Credential current = credentials.get(position);
        holder.titleText.setText(current.getTitle());
        holder.usernameText.setText(current.getUsername());

        updateTick(holder, current);

        // The Reveal & Copy Button
        holder.revealButton.setOnClickListener(v -> {
            revealedItemIds.add(current.getId());

            // Generate and Copy
            String code = TotpEngine.generateTOTP(current.getTotpSecret());
            ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("2FA Code", code);
            clipboard.setPrimaryClip(clip);

            Toast.makeText(context, "Code Copied!", Toast.LENGTH_SHORT).show();

            // Instantly update just this row
            notifyItemChanged(position, "TICK");
        });
    }

    // THE MAGIC: This is the "Partial Rebind" (Runs every 1 second without lagging the scroll)
    @Override
    public void onBindViewHolder(@NonNull AuthViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains("TICK")) {
            updateTick(holder, credentials.get(position));
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    private void updateTick(AuthViewHolder holder, Credential cred) {
        int timeRemaining = TotpEngine.getRemainingSeconds();

        // Android's CircularProgressIndicator fills up, we want it to drain down
        holder.progressTimer.setProgressCompat(timeRemaining, true);

        // SECURITY: Auto-hide the code the exact moment the 30-second window resets
        if (timeRemaining == 30) {
            revealedItemIds.remove(cred.getId());
        }

        if (revealedItemIds.contains(cred.getId())) {
            String code = TotpEngine.generateTOTP(cred.getTotpSecret());
            // Format it nicely like "123 456"
            holder.codeText.setText(code.substring(0, 3) + " " + code.substring(3));
            holder.codeText.setTextColor(android.graphics.Color.parseColor("#FFFFFF")); // Turn white when revealed
        } else {
            holder.codeText.setText("••• •••");
            // Use your vault accent color when hidden (using hex as a fallback here)
            holder.codeText.setTextColor(context.getResources().getColor(R.color.vault_accent, context.getTheme()));
        }
    }

    @Override
    public int getItemCount() {
        return credentials.size();
    }

    class AuthViewHolder extends RecyclerView.ViewHolder {
        private final TextView titleText;
        private final TextView usernameText;
        private final TextView codeText;
        private final CircularProgressIndicator progressTimer;
        private final View revealButton;

        public AuthViewHolder(View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.text_auth_title);
            usernameText = itemView.findViewById(R.id.text_auth_username);
            codeText = itemView.findViewById(R.id.text_auth_code);
            progressTimer = itemView.findViewById(R.id.progress_auth_timer);
            revealButton = itemView.findViewById(R.id.btn_reveal_code);
        }
    }
}