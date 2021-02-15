package de.cotech.hw.fido.example

import android.annotation.SuppressLint
import android.os.AsyncTask
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import de.cotech.hw.fido.FidoAuthenticateRequest
import de.cotech.hw.fido.ui.FidoDialogFragment
import de.cotech.hw.fido.util.VerifiedFidoAuthenticateResponse
import de.cotech.hw.fido.util.VerifiedFidoRegisterResponse
import kotlinx.android.synthetic.main.fragment_fido_u2f.*
import java.io.IOException
import java.util.*

class FidoU2fFragment : Fragment() {
    // A simple interface to a (fake) FIDO server backend. See FidoFakeServerInteractor for details.
    private val fidoFakeServerInteractor by lazy { FidoFakeServerInteractor(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_fido_u2f, container, false)

        val textDescription = view.findViewById<TextView>(R.id.textDescription)
        textDescription.movementMethod = LinkMovementMethod.getInstance()

        view.findViewById<View>(R.id.buttonFidoRegister).setOnClickListener { showFidoRegisterDialog() }
        view.findViewById<View>(R.id.buttonFidoAuthenticate).setOnClickListener {showFidoAuthenticateDialog() }

        return view
    }

    private fun showFidoRegisterDialog() {
        // Make a registration request to the server. In a real application, this would perform
        // an HTTP request. The server sends us a challenge (and some other data), that we proceed
        // to sign with our FIDO Security Key.
        val registerRequest = fidoFakeServerInteractor.fidoRegisterRequest(USERNAME)

        // This opens a UI fragment, which takes care of the user interaction as well as all FIDO
        // internal operations for us, and triggers a callback to #onRegisterResponse(FidoRegisterResponse).
        val fidoDialogFragment = FidoDialogFragment.newInstance(registerRequest)
        fidoDialogFragment.setFidoRegisterCallback(onFidoRegisterCallback)
        fidoDialogFragment.show(requireFragmentManager())
    }


    private fun showFidoAuthenticateDialog() {
        // Make an authentication request to the server. In a real application, this would perform
        // an HTTP request. The server will send us a challenge based on the FIDO key we registered
        // before (see above), asking us to prove we still have the same key.

        val authenticateRequest: FidoAuthenticateRequest = try {
            fidoFakeServerInteractor.fidoAuthenticateRequest(USERNAME)
        } catch (e: NoSuchElementException) {
            Toast.makeText(requireContext(), "No FIDO key registered - use register operation first!", Toast.LENGTH_LONG).show()
            return
        }

        // This opens a UI fragment, which takes care of the user interaction as well as all FIDO internal
        // operations for us, and triggers a callback to #onAuthenticateResponse(FidoAuthenticateResponse).
        val fidoDialogFragment = FidoDialogFragment.newInstance(authenticateRequest)
        fidoDialogFragment.setFidoAuthenticateCallback(onFidoAuthenticateCallback)
        fidoDialogFragment.show(requireFragmentManager())
    }

    @SuppressLint("StaticFieldLeak")
    private val onFidoRegisterCallback = FidoDialogFragment.OnFidoRegisterCallback {
        // Process the result in the background. Probably more complicated in a real application
        object : AsyncTask<Void?, Void?, VerifiedFidoRegisterResponse?>() {
            override fun doInBackground(dummy: Array<Void?>): VerifiedFidoRegisterResponse? {
                return try {
                    // Forward the registration response from the FIDO Security Key to our server application.
                    // The server will perform some checks, and then remember this FIDO key as a registered
                    // login mechanism for this user.
                    fidoFakeServerInteractor.fidoRegisterFinish(USERNAME, it)
                } catch (e: IOException) {
                    Log.e(FidoExampleApplication.TAG, "IOException", e)
                    null
                }
            }

            override fun onPostExecute(verifiedRegisterResponse: VerifiedFidoRegisterResponse?) {
                if (verifiedRegisterResponse != null) {
                    showDebugInfo(verifiedRegisterResponse)
                    Toast.makeText(requireContext(), "Registration successful!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Register operation failed!", Toast.LENGTH_LONG).show()
                }
            }
        }.execute()
    }

    @SuppressLint("StaticFieldLeak")
    val onFidoAuthenticateCallback = FidoDialogFragment.OnFidoAuthenticateCallback {
        // Process the result in the background. Probably more complicated in a real application
        object : AsyncTask<Void?, Void?, VerifiedFidoAuthenticateResponse?>() {
            override fun doInBackground(dummy: Array<Void?>): VerifiedFidoAuthenticateResponse? {
                return try {
                    // Forward the authentication response from the FIDO Security Key to our server application.
                    // The server will check that the signature matches the FIDO key we registered with, and if
                    // so we have successfully logged in.
                    fidoFakeServerInteractor.fidoAuthenticateFinish(USERNAME, it)
                } catch (e: IOException) {
                    Log.e(FidoExampleApplication.TAG, "IOException", e)
                    null
                }
            }

            override fun onPostExecute(verifiedAuthResponse: VerifiedFidoAuthenticateResponse?) {
                if (verifiedAuthResponse != null) {
                    showDebugInfo(verifiedAuthResponse)
                    Toast.makeText(requireContext(), "Authentication successful!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(requireContext(), "Authentication operation failed!", Toast.LENGTH_LONG).show()
                }
            }
        }.execute()
    }

    private fun showDebugInfo(debugObject: Any) {
        // Simply output the String representation of whatever object we get, in the UI and logcat
        Log.d(FidoExampleApplication.TAG, String.format("%s: %s", debugObject.javaClass.simpleName, debugObject))
        textLog.text = debugObject.toString()
    }

    companion object {
        fun newInstance() = FidoU2fFragment()
        private const val USERNAME = "testuser"
    }
}