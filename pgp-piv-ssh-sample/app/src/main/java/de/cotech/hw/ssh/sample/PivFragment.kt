package de.cotech.hw.ssh.sample

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import de.cotech.hw.piv.PivSecurityKey
import de.cotech.hw.piv.PivSecurityKeyDialogFragment
import de.cotech.hw.secrets.PinProvider
import de.cotech.hw.ui.SecurityKeyDialogInterface
import de.cotech.hw.ui.SecurityKeyDialogOptions
import de.cotech.hw.util.Hex
import java.nio.charset.Charset

class PivFragment : Fragment() {
    private lateinit var inputEditText: EditText
    private lateinit var outputEditText: EditText

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_piv, container, false)
        inputEditText = view.findViewById(R.id.editTextInput)
        outputEditText = view.findViewById(R.id.editTextOutput)

        val textDescription = view.findViewById<TextView>(R.id.textDescription)
        textDescription.movementMethod = LinkMovementMethod.getInstance()

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

        val securityKeyDialogFragment = PivSecurityKeyDialogFragment.newInstance(options)
        securityKeyDialogFragment.setSecurityKeyDialogCallback(SecurityKeyDialogInterface.SecurityKeyDialogCallback { dialogInterface, securityKey: PivSecurityKey, pinProvider ->
            auth(dialogInterface, securityKey, pinProvider)
        })
        securityKeyDialogFragment.show(requireFragmentManager())
    }

    private fun auth(dialogInterface: SecurityKeyDialogInterface, securityKey: PivSecurityKey, pinProvider: PinProvider?) {
        val authenticator = securityKey.createSecurityKeyAuthenticator(pinProvider)
        val challenge = inputEditText.text.toString().toByteArray(Charset.forName("UTF-8"))
        val response = authenticator.authenticateWithDigest(challenge, "SHA-256")

        outputEditText.setText(Hex.encodeHexString(response))
        dialogInterface.successAndDismiss()
    }

    companion object {
        fun newInstance() = PivFragment()
    }
}