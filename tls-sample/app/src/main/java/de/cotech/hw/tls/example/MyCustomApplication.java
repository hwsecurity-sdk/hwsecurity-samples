package de.cotech.hw.tls.example;


import android.app.Application;

import de.cotech.hw.SecurityKeyManager;
import de.cotech.hw.SecurityKeyManagerConfig;

public class MyCustomApplication extends Application {

    public static final String TAG = "TLS";

    @Override
    public void onCreate() {
        super.onCreate();

        SecurityKeyManagerConfig config = new SecurityKeyManagerConfig.Builder()
                .setEnableDebugLogging(BuildConfig.DEBUG)
                .build();
        SecurityKeyManager.getInstance().init(this, config);
    }
}