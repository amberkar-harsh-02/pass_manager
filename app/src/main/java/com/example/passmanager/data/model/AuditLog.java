package com.example.passmanager.data.model;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "audit_log_table")
public class AuditLog {

    @PrimaryKey(autoGenerate = true)
    private int id;

    private long timestamp;
    private String eventType; // e.g., "PIN_ENTRY", "BIOMETRIC"
    private boolean isSuccessful;

    public AuditLog(long timestamp, String eventType, boolean isSuccessful) {
        this.timestamp = timestamp;
        this.eventType = eventType;
        this.isSuccessful = isSuccessful;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public long getTimestamp() { return timestamp; }
    public String getEventType() { return eventType; }
    public boolean isSuccessful() { return isSuccessful; }
}