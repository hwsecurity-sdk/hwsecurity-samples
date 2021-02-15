package de.cotech.hw.database.sample.ui;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import de.cotech.hw.database.sample.db.EncryptedDatabase;
import de.cotech.hw.openpgp.storage.AndroidPreferencesEncryptedSessionStorage;

@SuppressLint("Registered")
class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AndroidPreferencesEncryptedSessionStorage masterSecretPrefs = AndroidPreferencesEncryptedSessionStorage.getInstance(this);
        boolean hasNoSecret = !masterSecretPrefs.hasAnyEncryptedSessionSecret();
        if (hasNoSecret) {
            startSetup();
            return;
        }

        if (EncryptedDatabase.getInstance() == null) {
            decryptDatabase();
            return;
        }
    }

    void startSetup() {
        Intent intent = new Intent(this, SetupActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
        finish();
    }

    void decryptDatabase() {
        Intent intent = new Intent(this, DecryptActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        startActivity(intent);
        finish();
    }
}
