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

    // NEW: The 4-Tier Health Score (0=Weak, 1=Moderate, 2=Strong, 3=Very Strong)
    private int healthScore;

    // Update the constructor to accept the healthScore
    public Credential(String title, String username, String encryptedPassword, String encryptionIv, int healthScore) {
        this.title = title;
        this.username = username;
        this.encryptedPassword = encryptedPassword;
        this.encryptionIv = encryptionIv;
        this.healthScore = healthScore;
    }

    // Existing Getters and Setters...
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    public String getTitle() { return title; }
    public String getUsername() { return username; }
    public String getEncryptedPassword() { return encryptedPassword; }
    public String getEncryptionIv() { return encryptionIv; }

    // NEW: Getter and Setter for Health Score
    public int getHealthScore() { return healthScore; }
    public void setHealthScore(int healthScore) { this.healthScore = healthScore; }
}