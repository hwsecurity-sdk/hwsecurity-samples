package de.cotech.hw.database.sample.ui;


import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;

import de.cotech.hw.SecurityKeyCallback;
import de.cotech.hw.SecurityKeyManager;
import de.cotech.hw.database.sample.R;
import de.cotech.hw.database.sample.db.EncryptedDatabase;
import de.cotech.hw.openpgp.OpenPgpSecurityKey;
import de.cotech.hw.openpgp.OpenPgpSecurityKeyConnectionMode;
import de.cotech.hw.openpgp.pairedkey.PairedEncryptor;
import de.cotech.hw.openpgp.pairedkey.PairedSecurityKey;
import de.cotech.hw.secrets.AndroidPreferenceSimplePinProvider;
import de.cotech.hw.secrets.ByteSecretGenerator;
import de.cotech.hw.openpgp.storage.AndroidPreferencePairedSecurityKeyStorage;
import de.cotech.hw.openpgp.storage.AndroidPreferencesEncryptedSessionStorage;
import de.cotech.hw.openpgp.storage.EncryptedSessionStorage;
import de.cotech.hw.openpgp.storage.PairedSecurityKeyStorage;
import de.cotech.hw.secrets.ByteSecret;
import de.cotech.hw.secrets.PinProvider;

public class SetupActivity extends AppCompatActivity implements SecurityKeyCallback<OpenPgpSecurityKey> {
    private PinProvider pinProvider;
    private PairedSecurityKeyStorage pairedSecurityKeyStorage;

    private boolean showWipeDialog = true;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_setup);

        SecurityKeyManager.getInstance().registerCallback(
                OpenPgpSecurityKeyConnectionMode.getInstance(), this, this);

        pinProvider =
                AndroidPreferenceSimplePinProvider.getInstance(getApplicationContext());
        pairedSecurityKeyStorage =
                AndroidPreferencePairedSecurityKeyStorage.getInstance(getApplicationContext());
    }

    @Override
    public void onSecurityKeyDiscovered(@NonNull OpenPgpSecurityKey securityKey) {
        if (showWipeDialog && !securityKey.isSecurityKeyEmpty()) {
            DialogInterface.OnClickListener dialogClickListener = (dialog, which) -> {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        showWipeDialog = false;
                        break;

                    case DialogInterface.BUTTON_NEGATIVE:
                        break;
                }
            };

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage("The Security Key is NOT empty! Wipe and generate a new key?")
                    .setPositiveButton("Yes", dialogClickListener)
                    .setNegativeButton("No", dialogClickListener)
                    .show();
        } else {
            setupDatabase(securityKey);
        }
    }

    @Override
    public void onSecurityKeyDiscoveryFailed(@NonNull IOException exception) {
    }

    @Override
    public void onSecurityKeyDisconnected(@NonNull OpenPgpSecurityKey securityKey) {
    }

    private void setupDatabase(OpenPgpSecurityKey securityKey) {
        // TODO: use something better than AsyncTask in your real app!
        @SuppressLint("StaticFieldLeak")
        AsyncTask task = new AsyncTask<Object, Object, String>() {

            @Override
            protected String doInBackground(Object[] objects) {
                PairedSecurityKey pairedSecurityKey = pairAndStoreSecurityKey(securityKey);
                if (pairedSecurityKey == null) {
                    return "failed to generate keys and pair Security Key!";
                }

                ByteSecret secret = generateSecret();
                byte[] encryptedSecret = encryptToSecurityKey(pairedSecurityKey, secret);

                saveEncryptedSecret(pairedSecurityKey, encryptedSecret);

                EncryptedDatabase.decryptAndGetInstance(getApplicationContext(), secret);
                return "successfully paired key, encrypted database with random secret that is encrypted to the security key";
            }

            @Override
            protected void onPostExecute(String returnString) {
                super.onPostExecute(returnString);
                Toast.makeText(SetupActivity.this, returnString, Toast.LENGTH_LONG).show();

                Intent intent = new Intent(SetupActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        };
        task.execute();
    }

    private PairedSecurityKey pairAndStoreSecurityKey(OpenPgpSecurityKey securityKey) {
        try {
            // OpenPgpSecurityKey operations are blocking, consider executing them in a new thread
            PairedSecurityKey pairedSecurityKey = securityKey.setupPairedKey(pinProvider);
            // Store the pairedSecurityKey. That way we can use it for encryption at any point
            pairedSecurityKeyStorage.addPairedSecurityKey(pairedSecurityKey);

            return pairedSecurityKey;
        } catch (IOException e) {
            return null;
        }
    }

    public ByteSecret generateSecret() {
        ByteSecretGenerator secretGenerator = ByteSecretGenerator.getInstance();
        return secretGenerator.createRandom(32);
    }

    public byte[] encryptToSecurityKey(PairedSecurityKey pairedSecurityKey, ByteSecret secret) {
        return new PairedEncryptor(pairedSecurityKey).encrypt(secret);
    }

    private void saveEncryptedSecret(PairedSecurityKey pairedSecurityKey, byte[] encryptedSecret) {
        EncryptedSessionStorage encryptedSessionStorage =
                AndroidPreferencesEncryptedSessionStorage.getInstance(getApplicationContext());
        encryptedSessionStorage.setEncryptedSessionSecret(
                pairedSecurityKey.getSecurityKeyAid(), encryptedSecret);
    }
}
