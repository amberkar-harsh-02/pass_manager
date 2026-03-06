package com.example.passmanager.data.local;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.passmanager.data.model.AuditLog;
import com.example.passmanager.data.model.Credential;

// 1. We bump the version to 2 and add AuditLog.class to the entities list
@Database(entities = {Credential.class, AuditLog.class}, version = 2, exportSchema = false)
public abstract class VaultDatabase extends RoomDatabase {

    public abstract CredentialDao credentialDao();
    public abstract AuditLogDao auditLogDao(); // Expose the new DAO

    private static volatile VaultDatabase INSTANCE;

    // 2. THE SAFE MIGRATION SCRIPT
    // This tells Room exactly how to build the new table without touching the existing passwords
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `audit_log_table` (" +
                    "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                    "`timestamp` INTEGER NOT NULL, " +
                    "`eventType` TEXT, " +
                    "`isSuccessful` INTEGER NOT NULL)");
        }
    };

    public static VaultDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (VaultDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    VaultDatabase.class, "password_vault_database")
                            .addMigrations(MIGRATION_1_2) // 3. Inject the migration here!
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}