package com.example.passmanager.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.passmanager.R;
import com.example.passmanager.data.model.Credential;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.List;

public class CredentialAdapter extends RecyclerView.Adapter<CredentialAdapter.CredentialHolder> {

    private List<Credential> credentials = new ArrayList<>();
    private OnItemClickListener listener;

    @NonNull
    @Override
    public CredentialHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View itemView = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_credential, parent, false);
        return new CredentialHolder(itemView);
    }

    @Override
    public void onBindViewHolder(@NonNull CredentialHolder holder, int position) {
        Credential currentCredential = credentials.get(position);
        holder.textViewTitle.setText(currentCredential.getTitle());
        holder.textViewUsername.setText(currentCredential.getUsername());

        int score = currentCredential.getHealthScore();

        if (score == 0) {
            holder.textHealthBadge.setText("WEAK");
            holder.textHealthBadge.setTextColor(android.graphics.Color.parseColor("#FF5252")); // Threat Red
        } else if (score == 1) {
            holder.textHealthBadge.setText("MODERATE");
            holder.textHealthBadge.setTextColor(android.graphics.Color.parseColor("#FFB142")); // Warning Orange
        } else if (score == 2) {
            holder.textHealthBadge.setText("STRONG");
            holder.textHealthBadge.setTextColor(android.graphics.Color.parseColor("#34ACE0")); // Secure Blue
        } else {
            holder.textHealthBadge.setText("VERY STRONG");
            holder.textHealthBadge.setTextColor(android.graphics.Color.parseColor("#33D9B2")); // Bulletproof Green
        }
    }

    @Override
    public int getItemCount() {
        return credentials.size();
    }

    // --- THE HELPER METHOD IS SAFELY HERE ---
    public Credential getCredentialAt(int position) {
        return credentials.get(position);
    }
    // ----------------------------------------

    public void setCredentials(List<Credential> credentials) {
        this.credentials = credentials;
        notifyDataSetChanged();
    }

    public void filterList(List<Credential> filteredList) {
        this.credentials = filteredList;
        notifyDataSetChanged();
    }

    public interface OnItemClickListener {
        void onItemClick(Credential credential);
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.listener = listener;
    }

    // --- THE INNER CLASS IS SEPARATED DOWN HERE ---
    class CredentialHolder extends RecyclerView.ViewHolder {
        private android.widget.TextView textViewTitle;
        private android.widget.TextView textViewUsername;
        private android.widget.TextView textHealthBadge;

        public CredentialHolder(View itemView) {
            super(itemView);
            // Notice we removed "_view" from these two IDs so they match the XML perfectly!
            textViewTitle = itemView.findViewById(R.id.text_title);
            textViewUsername = itemView.findViewById(R.id.text_username);
            textHealthBadge = itemView.findViewById(R.id.text_health_badge);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (listener != null && position != RecyclerView.NO_POSITION) {
                    listener.onItemClick(credentials.get(position));
                }
            });
        }
    }
}