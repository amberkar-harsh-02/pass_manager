package com.example.passmanager.data.model;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.UUID;

@Entity(tableName = "credentials_table")
public class Credential {

    @PrimaryKey
    @NonNull
    private String id;
    private String title;
    private String username;
    private String encryptedPassword;
    private String encryptionIv;
    private String category;
    private long lastUpdated;

    public Credential() {
        this.id = UUID.randomUUID().toString();
        this.lastUpdated = System.currentTimeMillis();
    }

    @NonNull
    public String getId() { return id; }
    public void setId(@NonNull String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEncryptedPassword() { return encryptedPassword; }
    public void setEncryptedPassword(String encryptedPassword) { this.encryptedPassword = encryptedPassword; }

    public String getEncryptionIv() { return encryptionIv; }
    public void setEncryptionIv(String encryptionIv) { this.encryptionIv = encryptionIv; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public long getLastUpdated() { return lastUpdated; }
    public void setLastUpdated(long lastUpdated) { this.lastUpdated = lastUpdated; }
}