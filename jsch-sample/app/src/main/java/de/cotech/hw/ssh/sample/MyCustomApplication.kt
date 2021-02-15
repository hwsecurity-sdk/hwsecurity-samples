package de.cotech.hw.ssh.sample

import android.app.Application
import de.cotech.hw.SecurityKeyManager
import de.cotech.hw.SecurityKeyManagerConfig

class MyCustomApplication : Application() {

    companion object {
        const val TAG = "SSH"
    }

    override fun onCreate() {
        super.onCreate()

        val securityKeyManager = SecurityKeyManager.getInstance()
        val config = SecurityKeyManagerConfig.Builder()
            .setEnableDebugLogging(BuildConfig.DEBUG)
            .build()
        securityKeyManager.init(this, config)
    }
}
