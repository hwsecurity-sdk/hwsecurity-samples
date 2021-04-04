package de.cotech.hw.ssh.sample

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.annotation.UiThread
import androidx.fragment.app.Fragment
import de.cotech.hw.openpgp.OpenPgpSecurityKey
import de.cotech.hw.openpgp.OpenPgpSecurityKeyDialogFragment
import de.cotech.hw.secrets.PinProvider
import de.cotech.hw.ui.SecurityKeyDialogInterface
import de.cotech.hw.ui.SecurityKeyDialogOptions
import de.cotech.hw.util.Hex
import kotlinx.android.synthetic.main.fragment_openpgp.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.IOException
import java.nio.charset.Charset

class OpenPgpFragment : Fragment() {
    private lateinit var inputEditText: EditText
    private lateinit var outputEditText: EditText

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_openpgp, container, false)
        inputEditText = view.findViewById(R.id.editTextInput)
        outputEditText = view.findViewById(R.id.editTextOutput)

        val textDescription = view.findViewById<TextView>(R.id.textDescription)
        textDescription.movementMethod = LinkMovementMethod.getInstance()

        view.findViewById<View>(R.id.buttonSetup).setOnClickListener { showSetupDialog() }
        view.findViewById<View>(R.id.buttonAuth).setOnClickListener { showAuthDialog() }

        return view
    }

    private fun showAuthDialog() {
        val options = SecurityKeyDialogOptions.builder()
                .setPinMode(SecurityKeyDialogOptions.PinMode.PIN_INPUT)
                .setShowReset(true)
                .setFormFactor(SecurityKeyDialogOptions.FormFactor.SECURITY_KEY)
                .setPreventScreenshots(!BuildConfig.DEBUG)
                .build()

        val securityKeyDialogFragment = OpenPgpSecurityKeyDialogFragment.newInstance(options)
        securityKeyDialogFragment.setSecurityKeyDialogCallback(SecurityKeyDialogInterface.SecurityKeyDialogCallback { dialogInterface, securityKey: OpenPgpSecurityKey, pinProvider ->
            auth(dialogInterface, securityKey, pinProvider)
        })
        securityKeyDialogFragment.show(requireFragmentManager())
    }

    private fun auth(dialogInterface: SecurityKeyDialogInterface, securityKey: OpenPgpSecurityKey, pinProvider: PinProvider?) {
        val securityKeyAuthenticator = securityKey.createSecurityKeyAuthenticator(pinProvider)
//        val publicKey = securityKeyAuthenticator.retrievePublicKey()

        val challenge = inputEditText.text.toString().toByteArray(Charset.forName("UTF-8"))

        val signature = securityKeyAuthenticator.authenticateWithDigest(challenge, "SHA-512")

        outputEditText.setText(Hex.encodeHexString(signature))
        dialogInterface.successAndDismiss()
    }

    private fun showSetupDialog() {
        val algorithm: OpenPgpSecurityKey.AlgorithmConfig = when (spinnerAlgorithm.selectedItem) {
            "RSA 2048" -> {
                OpenPgpSecurityKey.AlgorithmConfig.RSA_2048_UPLOAD
            }
            "ECC P-256" -> {
                OpenPgpSecurityKey.AlgorithmConfig.NIST_P256_GENERATE_ON_HARDWARE
            }
            "ECC P-384" -> {
                OpenPgpSecurityKey.AlgorithmConfig.NIST_P384_GENERATE_ON_HARDWARE
            }
            "ECC P-521" -> {
                OpenPgpSecurityKey.AlgorithmConfig.NIST_P521_GENERATE_ON_HARDWARE
            }
            "Curve 25519" -> {
                OpenPgpSecurityKey.AlgorithmConfig.CURVE25519_GENERATE_ON_HARDWARE
            }
            else -> {
                OpenPgpSecurityKey.AlgorithmConfig.RSA_2048_UPLOAD
            }
        }

        val options = SecurityKeyDialogOptions.builder()
                .setPinMode(SecurityKeyDialogOptions.PinMode.SETUP)
                .setFormFactor(SecurityKeyDialogOptions.FormFactor.SECURITY_KEY)
                .setPreventScreenshots(!BuildConfig.DEBUG)
                .build()

        val securityKeyDialogFragment = OpenPgpSecurityKeyDialogFragment.newInstance(options)
        securityKeyDialogFragment.setSecurityKeyDialogCallback(SecurityKeyDialogInterface.SecurityKeyDialogCallback { dialogInterface, securityKey: OpenPgpSecurityKey, pinProvider ->
            setupSecurityKey(dialogInterface, securityKey, pinProvider!!, algorithm)
        })
        securityKeyDialogFragment.show(requireFragmentManager())
    }

    @UiThread
    private fun setupSecurityKey(
            dialogInterface: SecurityKeyDialogInterface,
            securityKey: OpenPgpSecurityKey,
            pinProvider: PinProvider,
            algorithm: OpenPgpSecurityKey.AlgorithmConfig
    ) = GlobalScope.launch(Dispatchers.Main) {
        val deferred = GlobalScope.async(Dispatchers.IO) {
            dialogInterface.postProgressMessage("Generating keys…")
            securityKey.setupPairedKey(pinProvider, algorithm)
            dialogInterface.successAndDismiss()
        }

        try {
            deferred.await()
        } catch (e: IOException) {
            dialogInterface.postError(e)
        } catch (e: Exception) {
            Log.e(MyCustomApplication.TAG, "Exception", e)
        }
    }

    companion object {
        fun newInstance() = OpenPgpFragment()
    }
}