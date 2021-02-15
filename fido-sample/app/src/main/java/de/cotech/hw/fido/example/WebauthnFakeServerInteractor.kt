package de.cotech.hw.fido.example

import com.webauthn4j.WebAuthnManager
import com.webauthn4j.authenticator.Authenticator
import com.webauthn4j.authenticator.AuthenticatorImpl
import com.webauthn4j.converter.exception.DataConversionException
import com.webauthn4j.data.*
import com.webauthn4j.data.client.Origin
import com.webauthn4j.data.client.challenge.Challenge
import com.webauthn4j.server.ServerProperty
import de.cotech.hw.fido2.PublicKeyCredential
import de.cotech.hw.fido2.domain.AuthenticatorTransport
import de.cotech.hw.fido2.domain.PublicKeyCredentialDescriptor
import de.cotech.hw.fido2.domain.PublicKeyCredentialParameters
import de.cotech.hw.fido2.domain.PublicKeyCredentialRpEntity
import de.cotech.hw.fido2.domain.PublicKeyCredentialType
import de.cotech.hw.fido2.domain.PublicKeyCredentialUserEntity
import de.cotech.hw.fido2.domain.UserVerificationRequirement
import de.cotech.hw.fido2.domain.create.AttestationConveyancePreference
import de.cotech.hw.fido2.domain.create.AuthenticatorAttachment
import de.cotech.hw.fido2.domain.create.AuthenticatorAttestationResponse
import de.cotech.hw.fido2.domain.create.AuthenticatorSelectionCriteria
import de.cotech.hw.fido2.domain.create.PublicKeyCredentialCreationOptions
import de.cotech.hw.fido2.domain.get.AuthenticatorAssertionResponse
import de.cotech.hw.fido2.domain.get.PublicKeyCredentialRequestOptions
import java.io.IOException
import java.security.SecureRandom
import java.util.*

class WebauthnFakeServerException(message: String) : IOException(message)

/**
 * A simple stand-in for a FIDO-authentication enabled web server, for demonstration purposes.
 */
// for demonstration purposes
class WebauthnFakeServerInteractor {
    // The relying party id, which identifiers this specific (server-side) App.
    private val rpId: String = RP_ID

    // As a "database" of user logins, we simply remember a key handle and public key per registered username.
    private val registeredFidoKeyHandleByUsername = HashMap<String, RegisteredUser?>()

    private val webAuthnManager = WebAuthnManager.createNonStrictWebAuthnManager()

    // Registration
    fun webauthnRegisterRequest(username: String, verificationRequirement: UserVerificationRequirement, attestationConveyancePreference: AttestationConveyancePreference): PublicKeyCredentialCreationOptions {
        val registeredUser = getRegisteredUser(username)
        // Generate a challenge, and remember it for this user.
        val registerChallenge = generateChallenge()
        // Persist this challenge for the user, to check later on that the signed
        // challenge matches what we generated here.
        registeredUser.issuedRegistrationChallenge = registerChallenge
        return PublicKeyCredentialCreationOptions.create(
                PublicKeyCredentialRpEntity.create(RP_ID, "FIDO-Example Relying Party", null),
                PublicKeyCredentialUserEntity.create(registeredUser.id, username, username, null),
                registerChallenge,
                listOf(PublicKeyCredentialParameters.createDefaultEs256()),
                null,
                AuthenticatorSelectionCriteria.create(
                        AuthenticatorAttachment.CROSS_PLATFORM,
                        false,
                        verificationRequirement
                ),
                null,
                attestationConveyancePreference
        )
    }

    @Throws(IOException::class)
    fun webauthnRegisterFinish(username: String, publicKeyCredential: PublicKeyCredential): RegistrationData {
        val registeredUser = getRegisteredUser(username)

        val attestationResponse = publicKeyCredential.response() as AuthenticatorAttestationResponse
        val registrationData = try {
            val transports = setOf(AuthenticatorTransport.NFC.transport, AuthenticatorTransport.USB.transport)
            val registrationRequest = RegistrationRequest(attestationResponse.attestationObject(), attestationResponse.clientDataJson(), null, transports)
            webAuthnManager.parse(registrationRequest)
        } catch (e: DataConversionException) {
            // If you would like to handle WebAuthn data structure parse error, please catch DataConversionException
            throw e
        }

        val challenge: Challenge? = Challenge { registeredUser.issuedRegistrationChallenge }
        val serverProperty = ServerProperty(Origin(RP_ORIGIN), rpId, challenge, null)

        val userVerificationRequired = false
        val userPresenceRequired = true

        val registrationParameters = RegistrationParameters(serverProperty, userVerificationRequired, userPresenceRequired)
        val result = webAuthnManager.validate(registrationData, registrationParameters)

        val authenticator: Authenticator = AuthenticatorImpl(
                registrationData.attestationObject.authenticatorData.attestedCredentialData,
                registrationData.attestationObject.attestationStatement,
                registrationData.attestationObject.authenticatorData.signCount
        )

        // If successful, save the public key and key handle, which identify a registered FIDO Security Key for this user.
        registeredUser.addRegisteredFidoKey(authenticator)
        registeredUser.issuedRegistrationChallenge = null

        return result
    }

    // Authentication
    fun webauthnAuthenticateRequest(username: String?, verificationRequirement: UserVerificationRequirement): PublicKeyCredentialRequestOptions {
        // Get key handle and public key struct, which identifies the FIDO Security Key that the user registered before.
        val registeredUser = registeredFidoKeyHandleByUsername[username]
        if (registeredUser == null || !registeredUser.hasRegisteredKeys()) {
            throw WebauthnFakeServerException("No such registered user!")
        }

        // Generate an authentication challenge, and remember it for this user.
        val authChallenge = generateChallenge()

        // Persist this challenge for the user, to check later on that the signed challenge matches
        // what we generated here (see below)
        registeredUser.issuedAuthenticationChallenge = authChallenge
        return PublicKeyCredentialRequestOptions.create(authChallenge, null, RP_ID, registeredUser.credentialDescriptors, verificationRequirement)
    }

    @Throws(IOException::class)
    fun webauthnAuthenticateFinish(username: String?, publicKeyCredential: PublicKeyCredential): AuthenticationData {
        // Get key handle and public key struct, which identifies the FIDO Security Key that the user registered before.
        val registeredUser = registeredFidoKeyHandleByUsername[username]
                ?: throw IOException("No such registered user!")

        val authenticationData = try {
            val authenticateResponse = publicKeyCredential.response() as AuthenticatorAssertionResponse
            val authenticationRequest = AuthenticationRequest(
                    publicKeyCredential.rawId(),
                    authenticateResponse.userHandle(),
                    authenticateResponse.authenticatorData(),
                    authenticateResponse.clientDataJson(),
                    null,
                    authenticateResponse.signature()
            )
            webAuthnManager.parse(authenticationRequest)
        } catch (e: DataConversionException) {
            // If you would like to handle WebAuthn data structure parse error, please catch DataConversionException
            throw e
        }

        val challenge: Challenge? = Challenge { registeredUser.issuedAuthenticationChallenge }
        val serverProperty = ServerProperty(Origin.create(RP_ORIGIN), RP_ID, challenge, null)

        val userVerificationRequired = false
        val userPresenceRequired = true

        val authenticator = registeredUser.findRegisteredAuthenticatorByCredentialId(publicKeyCredential.rawId())
                ?: throw WebauthnFakeServerException("No such registered user!")

        val authenticationParameters = AuthenticationParameters(
                serverProperty,
                authenticator,
                userVerificationRequired,
                userPresenceRequired
        )

        val result = webAuthnManager.validate(authenticationData, authenticationParameters)
        registeredUser.issuedAuthenticationChallenge = null
        return result
    }

    // Helpers
    private fun generateChallenge(): ByteArray {
        // Returns a newly generated 16 bytes random challenge, in url-safe base64 encoding
        val challengeBytes = ByteArray(16)
        secureRandom.nextBytes(challengeBytes)
        return challengeBytes
    }

    private fun getRegisteredUser(username: String): RegisteredUser {
        registeredFidoKeyHandleByUsername[username]?.let { return it }
        val registeredUser = RegisteredUser()
        registeredFidoKeyHandleByUsername[username] = registeredUser
        return registeredUser
    }

    /** A registered user is identified by their public key and key handle.  */
    private class RegisteredUser {
        val id = ByteArray(16).also {
            secureRandom.nextBytes(it)
        }
        val registeredFidoKeys: MutableList<Authenticator> = ArrayList()
        var issuedAuthenticationChallenge: ByteArray? = null
        var issuedRegistrationChallenge: ByteArray? = null
        fun addRegisteredFidoKey(authenticator: Authenticator) {
            registeredFidoKeys.add(authenticator)
            while (registeredFidoKeys.size > 3) {
                registeredFidoKeys.removeAt(0)
            }
        }

        fun hasRegisteredKeys(): Boolean {
            return registeredFidoKeys.isNotEmpty()
        }

        val credentialDescriptors: List<PublicKeyCredentialDescriptor>
            get() = registeredFidoKeys.reversed().map { PublicKeyCredentialDescriptor.create(PublicKeyCredentialType.PUBLIC_KEY, it.attestedCredentialData.credentialId, null) }

        fun findRegisteredAuthenticatorByCredentialId(credentialId: ByteArray): Authenticator? {
            return registeredFidoKeys.find { it.attestedCredentialData.credentialId.contentEquals(credentialId) }
        }
    }

    companion object {
        private const val RP_ID = "fido-login.example.com"
        private const val RP_ORIGIN = "https://fido-login.example.com"
        private val secureRandom = SecureRandom()
    }
}