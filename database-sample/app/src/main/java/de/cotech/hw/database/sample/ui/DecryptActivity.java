package de.cotech.hw.database.sample.ui;


import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

import de.cotech.hw.SecurityKeyCallback;
import de.cotech.hw.SecurityKeyManager;
import de.cotech.hw.database.sample.R;
import de.cotech.hw.database.sample.db.EncryptedDatabase;
import de.cotech.hw.openpgp.pairedkey.PairedDecryptor;
import de.cotech.hw.openpgp.OpenPgpSecurityKey;
import de.cotech.hw.openpgp.OpenPgpSecurityKeyConnectionMode;
import de.cotech.hw.openpgp.pairedkey.PairedSecurityKey;
import de.cotech.hw.secrets.AndroidPreferenceSimplePinProvider;
import de.cotech.hw.openpgp.storage.AndroidPreferencePairedSecurityKeyStorage;
import de.cotech.hw.openpgp.storage.AndroidPreferencesEncryptedSessionStorage;
import de.cotech.hw.openpgp.storage.EncryptedSessionStorage;
import de.cotech.hw.openpgp.storage.PairedSecurityKeyStorage;
import de.cotech.hw.secrets.ByteSecret;
import de.cotech.hw.secrets.PinProvider;

public class DecryptActivity extends AppCompatActivity implements SecurityKeyCallback<OpenPgpSecurityKey> {
    private PinProvider pinProvider;
    private PairedSecurityKeyStorage pairedSecurityKeyStorage;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_unlock);

        SecurityKeyManager.getInstance().registerCallback(
                OpenPgpSecurityKeyConnectionMode.getInstance(), this, this);

        pinProvider =
                AndroidPreferenceSimplePinProvider.getInstance(getApplicationContext());
        pairedSecurityKeyStorage =
                AndroidPreferencePairedSecurityKeyStorage.getInstance(getApplicationContext());
    }

    @Override
    public void onSecurityKeyDiscovered(@NonNull OpenPgpSecurityKey securityKey) {
        decryptDatabase(securityKey);
    }

    @Override
    public void onSecurityKeyDiscoveryFailed(@NonNull IOException exception) {
    }

    @Override
    public void onSecurityKeyDisconnected(@NonNull OpenPgpSecurityKey securityKey) {
    }

    private void decryptDatabase(OpenPgpSecurityKey securityKey) {
        // TODO: use something better than AsyncTask in your real app!
        @SuppressLint("StaticFieldLeak")
        AsyncTask task = new AsyncTask<Object, Object, String>() {

            @Override
            protected String doInBackground(Object[] objects) {
                PairedSecurityKey pairedSecurityKey = getPairedSecurityKey();
                if (pairedSecurityKey == null) {
                    return "failed to get paired security key";
                }

                byte[] encryptedSecret = getEncryptedSecret(pairedSecurityKey);
                ByteSecret secret = decrypt(securityKey, encryptedSecret);
                if (secret == null) {
                    return "decrypt failed. Is the required key available?";
                }

                // decrypt database
                EncryptedDatabase.decryptAndGetInstance(getApplicationContext(), secret);
                return "successfully decrypted database!";
            }

            @Override
            protected void onPostExecute(String returnString) {
                super.onPostExecute(returnString);
                Toast.makeText(DecryptActivity.this, returnString, Toast.LENGTH_LONG).show();

                Intent intent = new Intent(DecryptActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        };
        task.execute();
    }

    private PairedSecurityKey getPairedSecurityKey() {
        // for simplicity, we assume a single paired security key
        return pairedSecurityKeyStorage.getAllPairedSecurityKeys().iterator().next();
    }

    private byte[] getEncryptedSecret(PairedSecurityKey pairedSecurityKey) {
        EncryptedSessionStorage encryptedSessionStorage =
                AndroidPreferencesEncryptedSessionStorage.getInstance(getApplicationContext());
        return encryptedSessionStorage.getEncryptedSessionSecret(pairedSecurityKey.getSecurityKeyAid());
    }

    public ByteSecret decrypt(OpenPgpSecurityKey securityKey, byte[] encryptedSecret) {
        try {
            PairedSecurityKey pairedSecurityKey = pairedSecurityKeyStorage.getPairedSecurityKey(
                    securityKey.getOpenPgpInstanceAid());
            if (pairedSecurityKey == null) {
                return null;
            }
            PairedDecryptor decryptor =
                    new PairedDecryptor(securityKey, pinProvider, pairedSecurityKey);

            return decryptor.decryptSessionSecret(encryptedSecret);
        } catch (IOException e) {
            return null;
        }
    }
}
