package de.cotech.hw.fido.example

import android.app.Application
import android.webkit.WebView
import de.cotech.hw.SecurityKeyManager
import de.cotech.hw.SecurityKeyManagerConfig


class FidoExampleApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        val config = SecurityKeyManagerConfig.Builder()
                //.setEnableDebugLogging(BuildConfig.DEBUG)
                // We cannot use DebugTree for logging Google releases with Proguard!
                .setSentryCaptureExceptionOnInternalError(true)
                .setAllowUntestedUsbDevices(true)
                .build()
        SecurityKeyManager.getInstance().init(this, config)
    }

    companion object {
        const val TAG = "FIDO"
    }
}