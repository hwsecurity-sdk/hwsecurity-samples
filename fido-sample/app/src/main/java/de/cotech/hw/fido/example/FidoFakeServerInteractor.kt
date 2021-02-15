package de.cotech.hw.fido.example

import android.content.Context
import de.cotech.hw.fido.*
import de.cotech.hw.fido.util.FidoAuthenticationVerifier
import de.cotech.hw.fido.util.FidoRegistrationVerifier
import de.cotech.hw.fido.util.VerifiedFidoAuthenticateResponse
import de.cotech.hw.fido.util.VerifiedFidoRegisterResponse
import de.cotech.hw.util.Arrays
import java.io.IOException
import java.security.SecureRandom
import java.util.*

/**
 * A simple stand-in for a FIDO-authentication enabled web server, for demonstration purposes.
 */
// for demonstration purposes
class FidoFakeServerInteractor(context: Context?) {
    // The FIDO facet id, which identifiers this specific App.
    private val fidoFacetId: String

    // As a "database" of user logins, we simply remember a key handle and public key per registered username.
    private val registeredFidoKeyHandleByUsername = HashMap<String, RegisteredUser?>()

    private val fidoAuthVerifier = FidoAuthenticationVerifier()
    private val fidoRegisterVerifier = FidoRegistrationVerifier()

    // Registration
    fun fidoRegisterRequest(username: String): FidoRegisterRequest {
        val registeredUser = getRegisteredUser(username)
        // Generate a challenge, and remember it for this user.
        val registerChallenge = generateChallenge()
        // Persist this challenge for the user, to check later on that the signed
        // challenge matches what we generated here.
        registeredUser!!.addRegistrationChallenge(registerChallenge)
        return FidoRegisterRequest.create(FIDO_APP_ID, fidoFacetId, registerChallenge)
    }

    @Throws(IOException::class)
    fun fidoRegisterFinish(username: String, registerResponse: FidoRegisterResponse): VerifiedFidoRegisterResponse {
        val registeredUser = getRegisteredUser(username)

        // Perform checks, if anything fails throw an exception
        val verifiedResponse = checkRegistrationChallengeForUsername(registeredUser, registerResponse)

        // If successful, save the public key and key handle, which identify a registered FIDO Security Key for this user.
        registeredUser!!.addRegisteredFidoKey(verifiedResponse.userPublicKey, verifiedResponse.keyHandle)
        return verifiedResponse
    }

    @Throws(IOException::class)
    private fun checkRegistrationChallengeForUsername(registeredUser: RegisteredUser?, registerResponse: FidoRegisterResponse): VerifiedFidoRegisterResponse {
        // Check that the signature in FidoRegisterResponse matches the client data as expected, and
        // that the signed challenge is one we generated for this user. We could also check the
        // attestation of the FIDO Security Key, to make sure it's from a trusted hardware vendor.
        val verifiedResponse = fidoRegisterVerifier.checkFidoRegisterResponse(FIDO_APP_ID, registerResponse)
        if (!registeredUser!!.checkAndRemoveRegistrationChallenge(verifiedResponse.challenge)) {
            throw IOException("Incorrect or expired challenge!")
        }
        // TODO check response correctly!
        return verifiedResponse
    }

    // Authentication
    fun fidoAuthenticateRequest(username: String?): FidoAuthenticateRequest {
        // Get key handle and public key struct, which identifies the FIDO Security Key that the user registered before.
        val registeredUser = registeredFidoKeyHandleByUsername[username]
        if (registeredUser == null || !registeredUser.hasRegisteredKeys()) {
            throw NoSuchElementException()
        }

        // Generate an authentication challenge, and remember it for this user.
        val authChallenge = generateChallenge()

        // Persist this challenge for the user, to check later on that the signed challenge matches
        // what we generated here (see below)
        registeredUser.addAuthenticationChallenge(authChallenge)
        return FidoAuthenticateRequest.create(FIDO_APP_ID, fidoFacetId, authChallenge, registeredUser.keyHandles)
    }

    @Throws(IOException::class)
    fun fidoAuthenticateFinish(username: String?, authenticateResponse: FidoAuthenticateResponse): VerifiedFidoAuthenticateResponse {
        // Get key handle and public key struct, which identifies the FIDO Security Key that the user registered before.
        val registeredUser = registeredFidoKeyHandleByUsername[username]
                ?: throw IOException("No such registered user!")

        // Perform checks, if anything fails throw an exception
        return checkAuthenticationChallengeForUsername(registeredUser, authenticateResponse)
    }

    @Throws(IOException::class)
    private fun checkAuthenticationChallengeForUsername(user: RegisteredUser,
                                                        authenticateResponse: FidoAuthenticateResponse): VerifiedFidoAuthenticateResponse {
        val registeredFidoKey = user.findRegisteredFidoKeyByHandle(authenticateResponse.keyHandle)

        // Check here that the signature in FidoAuthenticateResponse matches the client data,
        // and that the client data is what we expect (type, presence, and challenge).
        val verifiedResponse = fidoAuthVerifier.checkFidoAuthResponse(FIDO_APP_ID, registeredFidoKey!!.userPublicKey, authenticateResponse)
        if (!user.checkAndRemoveAuthenticationChallenge(verifiedResponse.challenge)) {
            throw IOException("Incorrect or expired challenge!")
        }
        if (!verifiedResponse.checkUserPresence()) {
            throw IOException("User presence flag not set!")
        }
        // TODO check response correctly!
        return verifiedResponse
    }

    // Helpers
    private fun generateChallenge(): String {
        // Returns a newly generated 16 bytes random challenge, in url-safe base64 encoding
        val secureRandom = SecureRandom()
        val challengeBytes = ByteArray(16)
        secureRandom.nextBytes(challengeBytes)
        return WebsafeBase64.encodeToString(challengeBytes)
    }

    private fun getRegisteredUser(username: String): RegisteredUser? {
        if (registeredFidoKeyHandleByUsername.containsKey(username)) {
            return registeredFidoKeyHandleByUsername[username]
        }
        val registeredUser = RegisteredUser()
        registeredFidoKeyHandleByUsername[username] = registeredUser
        return registeredUser
    }

    /** A registered user is identified by their public key and key handle.  */
    private class RegisteredUser {
        val registeredFidoKeys: MutableList<RegisteredFidoKey> = ArrayList()
        val issuedAuthenticationChallenges: MutableList<String> = ArrayList()
        val issuedRegistrationChallenges: MutableList<String> = ArrayList()
        fun addRegisteredFidoKey(userPublicKey: ByteArray?, keyHandle: ByteArray?) {
            registeredFidoKeys.add(RegisteredFidoKey(userPublicKey, keyHandle))
        }

        fun addAuthenticationChallenge(challenge: String) {
            issuedAuthenticationChallenges.add(challenge)
        }

        fun checkAndRemoveAuthenticationChallenge(challenge: String?): Boolean {
            return issuedAuthenticationChallenges.remove(challenge)
        }

        fun addRegistrationChallenge(challenge: String) {
            issuedRegistrationChallenges.add(challenge)
        }

        fun checkAndRemoveRegistrationChallenge(challenge: String?): Boolean {
            return issuedRegistrationChallenges.remove(challenge)
        }

        fun hasRegisteredKeys(): Boolean {
            return !registeredFidoKeys.isEmpty()
        }

        val keyHandles: List<ByteArray?>
            get() {
                val result = ArrayList<ByteArray?>()
                for (registeredFidoKey in registeredFidoKeys) {
                    result.add(registeredFidoKey.keyHandle)
                }
                return result
            }

        fun findRegisteredFidoKeyByHandle(keyHandle: ByteArray?): RegisteredFidoKey? {
            for (registeredFidoKey in registeredFidoKeys) {
                if (Arrays.areEqual(keyHandle, registeredFidoKey.keyHandle)) {
                    return registeredFidoKey
                }
            }
            return null
        }
    }

    private class RegisteredFidoKey(val userPublicKey: ByteArray?, val keyHandle: ByteArray?)

    companion object {
        // A FIDO AppID that identifies our "application" as a whole. See https://developers.yubico.com/U2F/App_ID.html
        private const val FIDO_APP_ID = "https://fido-login.example.com/app-id.json"
    }

    init {
        // Generate the FacetID that identifiers this particular App. This is based on the signing key,
        // and thus uniquely identifies this App.
        fidoFacetId = FidoFacetIdUtil.getFacetIdForApp(context)
    }
}