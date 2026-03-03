package com.example.passmanager.repository;

import android.app.Application;
import androidx.lifecycle.LiveData;
import com.example.passmanager.data.local.CredentialDao;
import com.example.passmanager.data.local.VaultDatabase;
import com.example.passmanager.data.model.Credential;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CredentialRepository {
    private CredentialDao credentialDao;
    private LiveData<List<Credential>> allCredentials;
    private ExecutorService executorService;

    public CredentialRepository(Application application) {
        VaultDatabase db = VaultDatabase.getDatabase(application);
        credentialDao = db.credentialDao();
        allCredentials = credentialDao.getAllCredentials();
        executorService = Executors.newSingleThreadExecutor();
    }

    public LiveData<List<Credential>> getAllCredentials() {
        return allCredentials;
    }

    public void insert(Credential credential) {
        executorService.execute(() -> credentialDao.insertCredential(credential));
    }

    public void delete(Credential credential) {
        executorService.execute(() -> credentialDao.deleteCredential(credential));
    }

    public void update(Credential credential) {
        executorService.execute(() -> credentialDao.updateCredential(credential));
    }
}