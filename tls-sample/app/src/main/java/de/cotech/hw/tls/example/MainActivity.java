package de.cotech.hw.tls.example;


import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import javax.net.ssl.SSLContext;

import de.cotech.hw.SecurityKeyTlsClientCertificateAuthenticator;
import de.cotech.hw.piv.PivSecurityKey;
import de.cotech.hw.piv.PivSecurityKeyDialogFragment;
import de.cotech.hw.secrets.PinProvider;
import de.cotech.hw.ui.SecurityKeyDialogFragment;
import de.cotech.hw.ui.SecurityKeyDialogInterface;
import de.cotech.hw.ui.SecurityKeyDialogOptions;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;


public class MainActivity extends AppCompatActivity implements SecurityKeyDialogInterface.SecurityKeyDialogCallback<PivSecurityKey> {
    private TextView log;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        log = findViewById(R.id.textLog);

        findViewById(R.id.buttonAuth).setOnClickListener(v -> showSecurityKeyDialog());
    }

    private void showSecurityKeyDialog() {
        SecurityKeyDialogOptions options = SecurityKeyDialogOptions.builder()
                //.setPinLength(4) // security keys with a fixed PIN and PUK length improve the UX
                //.setPukLength(8)
                .setPreventScreenshots(!BuildConfig.DEBUG)
                .build();

        SecurityKeyDialogFragment<PivSecurityKey> securityKeyDialogFragment =
                PivSecurityKeyDialogFragment.newInstance(options);
        securityKeyDialogFragment.show(getSupportFragmentManager());
    }

    @Override
    public void onSecurityKeyDialogDiscovered(@NonNull SecurityKeyDialogInterface dialogInterface,
                                              @NonNull PivSecurityKey securityKey,
                                              @Nullable PinProvider pinProvider) throws IOException {
        try {
            SecurityKeyTlsClientCertificateAuthenticator clientCertificateAuthenticator =
                    securityKey.createSecurityKeyClientCertificateAuthenticator(pinProvider);
            SSLContext sslContext = clientCertificateAuthenticator.buildInitializedSslContext();

            // connection with OkHttp
            OkHttpClient httpClient = new OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.getSocketFactory())
                    .build();
            Request request = new Request.Builder()
                    .url("https://tls.hwsecurity.dev")
                    .build();
            Response response = httpClient.newCall(request).execute();

            showDebugInfo(response);
            dialogInterface.dismiss();
        } catch (CertificateException e) {
            Log.e(MyCustomApplication.TAG, "CertificateException", e);
        } catch (NoSuchAlgorithmException e) {
            Log.e(MyCustomApplication.TAG, "NoSuchAlgorithmException", e);
        } catch (KeyManagementException e) {
            Log.e(MyCustomApplication.TAG, "KeyManagementException", e);
        }
    }

    @Override
    public void onSecurityKeyDialogCancel() {
        showDebugInfo("Dialog cancelled.");
    }

    @Override
    public void onSecurityKeyDialogDismiss() {
    }

    private void showDebugInfo(Object debugObject) {
        // Simply output the String representation of whatever object we get, in the UI and logcat
        Log.d(MyCustomApplication.TAG, String.format("%s: %s", debugObject.getClass().getSimpleName(), debugObject));
        log.setText(debugObject.toString());
    }
}
