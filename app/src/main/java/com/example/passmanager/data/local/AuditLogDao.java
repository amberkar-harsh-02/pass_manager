package com.example.passmanager.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.passmanager.data.model.AuditLog;

import java.util.List;

@Dao
public interface AuditLogDao {

    @Insert
    void insertLog(AuditLog auditLog);

    // Pull all logs, most recent first
    @Query("SELECT * FROM audit_log_table ORDER BY timestamp DESC")
    LiveData<List<AuditLog>> getAllLogs();

    // In case we ever want to let the user clear their history
    @Query("DELETE FROM audit_log_table")
    void clearLogs();
}