package de.cotech.hw.fido.example

import android.app.Application
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import androidx.fragment.app.Fragment
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.webauthn4j.data.attestation.statement.AttestationStatement
import com.webauthn4j.data.attestation.statement.CertificateBaseAttestationStatement
import com.webauthn4j.validator.exception.ValidationException
import de.cotech.hw.fido2.PublicKeyCredential
import de.cotech.hw.fido2.PublicKeyCredentialCreate
import de.cotech.hw.fido2.PublicKeyCredentialGet
import de.cotech.hw.fido2.domain.UserVerificationRequirement
import de.cotech.hw.fido2.domain.create.AttestationConveyancePreference
import de.cotech.hw.fido2.ui.WebauthnDialogFragment
import de.cotech.hw.fido2.ui.WebauthnDialogOptions
import de.cotech.hw.util.Hex
import io.sentry.core.Sentry
import kotlinx.android.synthetic.main.fragment_webauthn.*
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException

class WebauthnViewModel(application: Application) : AndroidViewModel(application) {
    val serverInteractor = WebauthnFakeServerInteractor()
    val webauthnDialogOptionsBuilder = WebauthnDialogOptions.builder()!!
}

class WebauthnFragment : Fragment() {
    private val viewModel by lazy {
        ViewModelProvider.AndroidViewModelFactory.getInstance(requireActivity().application)
                .create(WebauthnViewModel::class.java)
    }
    private val viewModelScope
        get() = viewModel.viewModelScope
    private val coroutineContext = Dispatchers.Main + CoroutineExceptionHandler { _, e ->
        if (e is ValidationException || e is IOException) {
            Log.e(FidoExampleApplication.TAG, "Handling exception in coroutine context")
            handleGeneralError(e)
            return@CoroutineExceptionHandler
        }
        Sentry.captureException(e)

        throw e
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_webauthn, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        textDescription.movementMethod = LinkMovementMethod.getInstance()

        buttonFidoRegister.setOnClickListener { showRegisterDialog() }
        buttonFidoAuthenticate.setOnClickListener { showAuthenticateDialog() }

        checkboxConfigForceU2f.setOnCheckedChangeListener { _: CompoundButton?, checked: Boolean ->
            viewModel.webauthnDialogOptionsBuilder.setForceU2f(checked) }
    }

    private fun getVerificationRequirement(): UserVerificationRequirement {
        return when (spinnerConfigVerification.selectedItemPosition) {
            0 -> UserVerificationRequirement.DISCOURAGED
            1 -> UserVerificationRequirement.PREFERRED
            2 -> UserVerificationRequirement.REQUIRED
            else -> throw IllegalStateException("Unhandled resource case!")
        }
    }

    private fun getAttestationPreference(): AttestationConveyancePreference {
        return when (spinnerConfigAttestation.selectedItemPosition) {
            0 -> AttestationConveyancePreference.NONE
            1 -> AttestationConveyancePreference.DIRECT
            else -> throw IllegalStateException("Unhandled resource case!")
        }
    }

    private fun showRegisterDialog() {
        viewModelScope.launch(coroutineContext) {
            val dialogFragment = withContext(Dispatchers.IO) {
                // Make a registration request to the server. In a real application, this would perform
                // an HTTP request. The server sends us a challenge (and some other data), that we proceed
                // to sign with our FIDO2 Security Key.
                val registerRequest = viewModel.serverInteractor.webauthnRegisterRequest(USERNAME, getVerificationRequirement(), getAttestationPreference())

                // This opens a UI fragment, which takes care of the user interaction as well as all FIDO2
                // internal operations, and triggers a callback to #onMakeCredentialCallback(PublicKeyCredential).
                WebauthnDialogFragment.newInstance(
                        PublicKeyCredentialCreate.create(ORIGIN, registerRequest),
                        viewModel.webauthnDialogOptionsBuilder.build(),
                )
            }
            dialogFragment.setOnMakeCredentialCallback(onMakeCredentialCallback)
            dialogFragment.show(requireFragmentManager())
        }
    }

    private fun showAuthenticateDialog() {
        viewModelScope.launch(coroutineContext) {
            val dialogFragment = withContext(Dispatchers.IO) {
                // Make an authentication request to the server. In a real application, this would perform
                // an HTTP request. The server will send us a challenge based on the FIDO2 key we registered
                // before (see above), asking us to prove we still have the same key.
                val authenticateRequest = viewModel.serverInteractor.webauthnAuthenticateRequest(USERNAME, getVerificationRequirement())

                // This opens a UI fragment, which takes care of the user interaction as well as all FIDO2 internal
                // operations, and triggers a callback to #onGetAssertionCallback(PublicKeyCredential).
                WebauthnDialogFragment.newInstance(
                        PublicKeyCredentialGet.create(ORIGIN, authenticateRequest),
                        viewModel.webauthnDialogOptionsBuilder.build(),
                )
            }

            dialogFragment.setOnGetAssertionCallback(onGetAssertionCallback)
            dialogFragment.show(requireFragmentManager())
        }
    }

    private val onMakeCredentialCallback = WebauthnDialogFragment.OnMakeCredentialCallback { publicKeyCredential ->
        viewModelScope.launch(coroutineContext) {
            textLog.text = withContext(Dispatchers.IO) {
                val result = viewModel.serverInteractor.webauthnRegisterFinish(USERNAME, publicKeyCredential)
                val formattedStmt = formatAttestationStatement(result.attestationObject.attestationStatement)

                """
                    credentialId: ${result.attestationObject.authenticatorData.attestedCredentialData.credentialId.toHexString()}
                    clientDataJson:
${JSONObject(String(publicKeyCredential.response().clientDataJson())).toString(2).prependIndent("                        ")}
                    aaguid: ${result.attestationObject.authenticatorData.attestedCredentialData.aaguid}
                    publicKey: ${result.attestationObject.authenticatorData.attestedCredentialData.coseKey.publicKey.encoded.toHexString()}
                    transports: ${result.transports.map { it.value }.joinToString()}
                    userPresence: ${result.attestationObject.authenticatorData.isFlagUP}
                    userVerification: ${result.attestationObject.authenticatorData.isFlagUV}
                    signatureCount: ${result.attestationObject.authenticatorData.signCount}
                    attestation: ${result.attestationObject.format} 

                """.trimIndent() + formattedStmt
            }
        }
    }

    private fun formatAttestationStatement(stmt: AttestationStatement): String {
        return when (stmt) {
            is CertificateBaseAttestationStatement -> stmt.x5c.endEntityAttestationCertificate.certificate.toString()
            else -> ""
        }
    }

    private val onGetAssertionCallback = WebauthnDialogFragment.OnGetAssertionCallback { publicKeyCredential ->
        viewModelScope.launch(coroutineContext) {
            textLog.text = withContext(Dispatchers.IO) {
                val result = viewModel.serverInteractor.webauthnAuthenticateFinish(USERNAME, publicKeyCredential)
                """
                    credentialId: ${result.credentialId.toHexString()}
                    clientDataJson:
${JSONObject(String(publicKeyCredential.response().clientDataJson())).toString(2).prependIndent("                        ")}
                    userPresence: ${result.authenticatorData.isFlagUP}
                    userVerification: ${result.authenticatorData.isFlagUV}
                    signatureCount: ${result.authenticatorData.signCount}
                """.trimIndent()
            }
        }
    }

    private fun handleGeneralError(e: Throwable) {
        textLog.text = "${e.javaClass.simpleName}: ${e.message}"
    }

    companion object {
        fun newInstance() = WebauthnFragment()

        private const val USERNAME = "testuser"
        private const val ORIGIN = "https://fido-login.example.com"
    }
}

private fun ByteArray.toHexString() = Hex.encodeHexString(this)