package com.example.passmanager.data.local;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.passmanager.data.model.Credential;
import java.util.List;

@Dao
public interface CredentialDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertCredential(Credential credential);

    @Delete
    void deleteCredential(Credential credential);

    @Update
    void updateCredential(Credential credential);

    @Query("SELECT * FROM credentials_table ORDER BY title ASC")
    LiveData<List<Credential>> getAllCredentials();
}