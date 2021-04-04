package de.cotech.hw.ssh.sample

import android.app.Application
import de.cotech.hw.SecurityKeyManager
import de.cotech.hw.SecurityKeyManagerConfig
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

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

        // required to make SSHJ work with modern ciphers
        Security.removeProvider("BC") //first remove default os provider
        Security.addProvider(BouncyCastleProvider()) //add new provider
    }
}
