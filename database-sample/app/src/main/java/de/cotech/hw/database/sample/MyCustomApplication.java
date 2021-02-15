package de.cotech.hw.database.sample;


import android.app.Application;

import de.cotech.hw.SecurityKeyManager;
import de.cotech.hw.SecurityKeyManagerConfig;
import de.cotech.hw.openpgp.OpenPgpSecurityKeyConnectionMode;
import de.cotech.hw.openpgp.OpenPgpSecurityKeyConnectionModeConfig;

public class MyCustomApplication extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        OpenPgpSecurityKeyConnectionModeConfig pgpConfig = new OpenPgpSecurityKeyConnectionModeConfig.Builder()
                .build();
        OpenPgpSecurityKeyConnectionMode.setDefaultConfig(pgpConfig);

        SecurityKeyManagerConfig config = new SecurityKeyManagerConfig.Builder()
                .setEnableDebugLogging(BuildConfig.DEBUG)
                .build();
        SecurityKeyManager.getInstance().init(this, config);
    }
}