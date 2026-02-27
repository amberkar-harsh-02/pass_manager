package com.example.passmanager.data.local;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import com.example.passmanager.data.model.Credential;

@Database(entities = {Credential.class}, version = 1, exportSchema = false)
public abstract class VaultDatabase extends RoomDatabase {

    public abstract CredentialDao credentialDao();
    private static volatile VaultDatabase INSTANCE;

    public static VaultDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (VaultDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    VaultDatabase.class, "password_vault_database")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}