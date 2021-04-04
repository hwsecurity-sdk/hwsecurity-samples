package de.cotech.hw.ssh.sample

import android.os.Bundle
import android.os.SystemClock
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.AnyThread
import androidx.annotation.WorkerThread
import androidx.fragment.app.Fragment
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Logger
import de.cotech.hw.SecurityKeyAuthenticator
import de.cotech.hw.openpgp.OpenPgpSecurityKey
import de.cotech.hw.openpgp.OpenPgpSecurityKeyDialogFragment
import de.cotech.hw.piv.PivSecurityKey
import de.cotech.hw.piv.PivSecurityKeyDialogFragment
import de.cotech.hw.secrets.PinProvider
import de.cotech.hw.sshj.SecurityKeySshjAuthMethod
import de.cotech.hw.ui.SecurityKeyDialogInterface
import de.cotech.hw.ui.SecurityKeyDialogOptions
import kotlinx.android.synthetic.main.fragment_jsch.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.LoggerFactory
import net.schmizz.sshj.common.StreamCopier
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.PromiscuousVerifier
import java.io.ByteArrayOutputStream
import java.io.IOException

class SshjFragment : Fragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_sshj, container, false)

        val textDescription = view.findViewById<TextView>(R.id.textDescription)
        textDescription.movementMethod = LinkMovementMethod.getInstance()

        JSch.setLogger(object : Logger {
            override fun isEnabled(level: Int) = true
            override fun log(level: Int, message: String) {
                Log.d(MyCustomApplication.TAG, String.format("Jsch: $level: $message"))
            }
        })

        view.findViewById<View>(R.id.buttonConnect).setOnClickListener { showSecurityKeyDialog() }

        return view
    }

    private fun showSecurityKeyDialog() {
        when (spinnerCardType.selectedItem) {
            "OpenPGP" -> showOpenPgpSecurityKeyDialog()
            "PIV" -> showPivSecurityKeyDialog()
        }
    }

    private fun showPivSecurityKeyDialog() {
        val options = SecurityKeyDialogOptions.builder()
                //.setPinLength(4) // security keys with a fixed PIN and PUK length improve the UX
                //.setPukLength(8)
                .setShowReset(true) // show button to reset/unblock of PIN using the PUK
                .setFormFactor(SecurityKeyDialogOptions.FormFactor.SECURITY_KEY)
                .setAllowKeyboard(false)
                .setPreventScreenshots(!BuildConfig.DEBUG)
                .build()

        val securityKeyDialogFragment = PivSecurityKeyDialogFragment.newInstance(options)
        securityKeyDialogFragment.setSecurityKeyDialogCallback(SecurityKeyDialogInterface.SecurityKeyDialogCallback { dialogInterface, securityKey: PivSecurityKey, pinProvider ->
            connectToSshPiv(dialogInterface, securityKey, pinProvider)
        })
        securityKeyDialogFragment.show(requireFragmentManager())
    }

    private fun showOpenPgpSecurityKeyDialog() {
        val options = SecurityKeyDialogOptions.builder()
                //.setPinLength(4) // security keys with a fixed PIN and PUK length improve the UX
                //.setPukLength(8)
                .setShowReset(true) // show button to reset/unblock of PIN using the PUK
                .setFormFactor(SecurityKeyDialogOptions.FormFactor.SECURITY_KEY)
                .setAllowKeyboard(true)
                .setPreventScreenshots(!BuildConfig.DEBUG)
                .build()

        val securityKeyDialogFragment = OpenPgpSecurityKeyDialogFragment.newInstance(options)
        securityKeyDialogFragment.setSecurityKeyDialogCallback(SecurityKeyDialogInterface.SecurityKeyDialogCallback { dialogInterface, securityKey: OpenPgpSecurityKey, pinProvider ->
            connectToSshOpenPgp(dialogInterface, securityKey, pinProvider)
        })
        securityKeyDialogFragment.show(requireFragmentManager())
    }

    private fun connectToSshPiv(
            dialogInterface: SecurityKeyDialogInterface,
            securityKey: PivSecurityKey,
            pinProvider: PinProvider?
    ) {
        val securityKeyAuthenticator = securityKey.createSecurityKeyAuthenticator(pinProvider)
        connectToSsh(dialogInterface, securityKeyAuthenticator)
    }

    private fun connectToSshOpenPgp(
            dialogInterface: SecurityKeyDialogInterface,
            securityKey: OpenPgpSecurityKey,
            pinProvider: PinProvider?
    ) {
        val securityKeyAuthenticator = securityKey.createSecurityKeyAuthenticator(pinProvider)
        connectToSsh(dialogInterface, securityKeyAuthenticator)
    }

    private fun connectToSsh(
            dialogInterface: SecurityKeyDialogInterface,
            securityKeyAuthenticator: SecurityKeyAuthenticator
    ) = GlobalScope.launch(Dispatchers.Main) {
        val loginName = textDataUser.text.toString()
        val loginHost = textDataHost.text.toString()
        textLog.text = ""

        dialogInterface.postProgressMessage("Retrieving public key/certificate from Security Key…")
        val deferred = GlobalScope.async(Dispatchers.IO) {
            val securityKeySshjAuthMethod = SecurityKeySshjAuthMethod(securityKeyAuthenticator)
            sshjConnection(dialogInterface, loginHost, loginName, securityKeySshjAuthMethod)
        }

        try {
            deferred.await()
        } catch (e: IOException) {
            dialogInterface.postError(e)
        } catch (e: Exception) {
            Log.e(MyCustomApplication.TAG, "Exception", e)
        }
    }

    @WorkerThread
    private fun sshjConnection(
            dialogInterface: SecurityKeyDialogInterface,
            loginHost: String,
            loginName: String,
            securityKeySshjAuthMethod: SecurityKeySshjAuthMethod
    ) {
        dialogInterface.postProgressMessage("Connecting to SSH server…")

        val sshClient = SSHClient()
        sshClient.timeout = TIMEOUT_MS_CONNECT

        // WARNING: This sample does not verify the host!
        sshClient.addHostKeyVerifier(PromiscuousVerifier())
        sshClient.connect(loginHost)
        val session: Session?

        sshClient.auth(loginName, securityKeySshjAuthMethod)
        session = sshClient.startSession()

        session.allocateDefaultPTY()
        val shell = session.startShell()

        val baos = ByteArrayOutputStream()
        baos.write("Server Output: ".toByteArray(), 0, 15)

        StreamCopier(shell.inputStream, baos, LoggerFactory.DEFAULT)
                .bufSize(shell.localMaxPacketSize)
                .spawn("stdout")

        StreamCopier(shell.errorStream, baos, LoggerFactory.DEFAULT)
                .bufSize(shell.localMaxPacketSize)
                .spawn("stderr")

        val startTime = SystemClock.elapsedRealtime()
        while (sshClient.isConnected) {
            if (SystemClock.elapsedRealtime() - startTime > MAX_CONNECTION_TIME) {
                appendToLog("SSH client automatically disconnected after $MAX_CONNECTION_TIME ms.")
                session?.close()
                sshClient.disconnect()
                break
            }
        }

        // close dialog after successful authentication
        dialogInterface.successAndDismiss()
        appendToLog("SSH connection successful!")
        appendToLog("")
        appendToLog(baos.toString())
    }

    @AnyThread
    private fun appendToLog(dataText: String) {
        Log.d(MyCustomApplication.TAG, String.format("text: %s", dataText))
        scrollLog.post {
            val appendedText = "${textLog.text}\n$dataText"
            textLog.text = appendedText
            scrollLog.fullScroll(View.FOCUS_DOWN)
            // scrollLog.smoothScrollTo(0, scrollLog.height)
        }
    }

    companion object {
        fun newInstance() = SshjFragment()

        const val MAX_CONNECTION_TIME = 2000
        const val TIMEOUT_MS_CONNECT = 10000
    }
}