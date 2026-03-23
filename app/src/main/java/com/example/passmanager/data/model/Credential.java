package com.example.passmanager.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "credentials_table")
public class Credential {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private String title;
    private String username;
    private String encryptedPassword;
    private String encryptionIv;
    private int healthScore;

    // NEW: The Authenticator Secret Key
    private String totpSecret;

    // Update the constructor to accept the totpSecret
    public Credential(String title, String username, String encryptedPassword, String encryptionIv, int healthScore, String totpSecret) {
        this.title = title;
        this.username = username;
        this.encryptedPassword = encryptedPassword;
        this.encryptionIv = encryptionIv;
        this.healthScore = healthScore;
        this.totpSecret = totpSecret; // Assign the new variable
    }

    // Existing Getters and Setters...
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTitle() { return title; }
    public String getUsername() { return username; }
    public String getEncryptedPassword() { return encryptedPassword; }
    public String getEncryptionIv() { return encryptionIv; }
    public int getHealthScore() { return healthScore; }
    public void setHealthScore(int healthScore) { this.healthScore = healthScore; }

    // NEW: Getter and Setter for TOTP Secret
    public String getTotpSecret() { return totpSecret; }
    public void setTotpSecret(String totpSecret) { this.totpSecret = totpSecret; }
}