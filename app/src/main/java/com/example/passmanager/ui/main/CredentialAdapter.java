package com.example.passmanager.ui.main;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.passmanager.R;
import com.example.passmanager.data.model.Credential;

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
        private TextView textViewTitle;
        private TextView textViewUsername;

        public CredentialHolder(View itemView) {
            super(itemView);
            textViewTitle = itemView.findViewById(R.id.text_view_title);
            textViewUsername = itemView.findViewById(R.id.text_view_username);

            itemView.setOnClickListener(v -> {
                int position = getAdapterPosition();
                if (listener != null && position != RecyclerView.NO_POSITION) {
                    listener.onItemClick(credentials.get(position));
                }
            });
        }
    }
}