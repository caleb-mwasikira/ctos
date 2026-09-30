package com.ctos.client

import com.ctos.client.data.SecureDropCrypto
import java.security.KeyFactory
import java.security.spec.MGF1ParameterSpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PSource
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The whole Secure Drop protocol (docs/secure-drop.md), against itself and against a second key
 * pair standing in for a recipient — the same shape resources/js/secure-drop-crypto.js's own
 * round trip takes in the browser, and app/Support/SecureDropKey.php's on the website.
 */
class SecureDropCryptoTest {
    @Test
    fun `a public key PEM is a real SPKI key, parseable on its own`() {
        val pair = SecureDropCrypto.generateKeyPair()

        assertTrue(pair.publicKeyPem.startsWith("-----BEGIN PUBLIC KEY-----"))
        assertTrue(pair.publicKeyPem.trimEnd().endsWith("-----END PUBLIC KEY-----"))

        val der = extractDer(pair.publicKeyPem, "PUBLIC KEY")
        val parsed = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(der))
        assertEquals(2048, (parsed as java.security.interfaces.RSAPublicKey).modulus.bitLength())
    }

    @Test
    fun `fingerprints are deterministic and different keys get different ones`() {
        val a = SecureDropCrypto.generateKeyPair()
        val b = SecureDropCrypto.generateKeyPair()

        val fingerprintA = SecureDropCrypto.fingerprintOfPublicKeyPem(a.publicKeyPem)

        assertEquals(64, fingerprintA.length)
        assertEquals(fingerprintA, SecureDropCrypto.fingerprintOfPublicKeyPem(a.publicKeyPem))
        assertNotEquals(fingerprintA, SecureDropCrypto.fingerprintOfPublicKeyPem(b.publicKeyPem))
    }

    @Test
    fun `the encrypted private key envelope only opens with the right passphrase`() {
        val pair = SecureDropCrypto.generateKeyPair()

        val envelope = SecureDropCrypto.encryptPrivateKey(pair.privateKey, "a very good passphrase")
        assertTrue(envelope.startsWith("-----BEGIN CTOS ENCRYPTED PRIVATE KEY-----"))

        val recovered = SecureDropCrypto.decryptPrivateKey(envelope, "a very good passphrase")
        // A key recovered correctly can still decrypt something the original key encrypted.
        val (ciphertext, iv, rawKey) = SecureDropCrypto.encryptFile("round trip".toByteArray())
        val wrapped = SecureDropCrypto.wrapAesKeyFor(pair.publicKeyPem, rawKey)
        assertContentEquals(rawKey, SecureDropCrypto.unwrapAesKey(recovered, wrapped))
        assertContentEquals("round trip".toByteArray(), SecureDropCrypto.decryptFile(ciphertext, iv, SecureDropCrypto.unwrapAesKey(recovered, wrapped)))

        assertFailsWith<IllegalArgumentException> {
            SecureDropCrypto.decryptPrivateKey(envelope, "the wrong passphrase")
        }
    }

    @Test
    fun `a passphrase opens its envelope whether typed pre-composed or as a decomposed dead-key sequence`() {
        val pair = SecureDropCrypto.generateKeyPair()
        // "café" as one browser (or one keyboard layout) would send it, vs. how a Linux dead-key
        // sequence can send the same visual text: "e" followed by a combining acute accent rather
        // than the single precomposed "é" character. Both must derive the same key.
        val precomposed = "café-passphrase-é"
        val decomposed = "café-passphrase-é"
        assertNotEquals(precomposed, decomposed) // sanity: genuinely different code point sequences

        val envelope = SecureDropCrypto.encryptPrivateKey(pair.privateKey, precomposed)
        val recovered = SecureDropCrypto.decryptPrivateKey(envelope, decomposed)

        val (ciphertext, iv, rawKey) = SecureDropCrypto.encryptFile("normalize me".toByteArray())
        val wrapped = SecureDropCrypto.wrapAesKeyFor(pair.publicKeyPem, rawKey)
        assertContentEquals("normalize me".toByteArray(), SecureDropCrypto.decryptFile(ciphertext, iv, SecureDropCrypto.unwrapAesKey(recovered, wrapped)))
    }

    @Test
    fun `an imported key pair behaves exactly like a generated one, for someone bringing their own`() {
        val generated = SecureDropCrypto.generateKeyPair()
        // Only this test needs the raw PKCS8 bytes (internal, same module): standing in for a
        // plain private key PEM someone already had, the way "bring your own key pair" starts.
        val plainPrivatePem = "-----BEGIN PRIVATE KEY-----\n" +
            Base64.getEncoder().encodeToString(generated.privateKey.key.encoded).chunked(64).joinToString("\n") +
            "\n-----END PRIVATE KEY-----"

        val imported = SecureDropCrypto.importKeyPair(generated.publicKeyPem, plainPrivatePem)
        val (ciphertext, iv, rawKey) = SecureDropCrypto.encryptFile("bring your own key".toByteArray())
        val wrapped = SecureDropCrypto.wrapAesKeyFor(imported.publicKeyPem, rawKey)

        assertContentEquals("bring your own key".toByteArray(), SecureDropCrypto.decryptFile(ciphertext, iv, SecureDropCrypto.unwrapAesKey(imported.privateKey, wrapped)))
    }

    @Test
    fun `the proof-of-possession challenge only the matching private key can answer`() {
        val pair = SecureDropCrypto.generateKeyPair()
        val nonce = "a 32 byte nonce, more or less..".toByteArray()

        // Standing in for the server (PHP's openssl_public_encrypt / Go's rsa.EncryptOAEP), both
        // of which use SHA-1 as OAEP's own padding hash — see SecureDropCrypto's own doc comment.
        val publicKey = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(extractDer(pair.publicKeyPem, "PUBLIC KEY")))
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKey, OAEPParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT))
        val challenge = Base64.getEncoder().encodeToString(cipher.doFinal(nonce))

        val proof = SecureDropCrypto.decryptChallenge(pair.privateKey, challenge)

        assertEquals(Base64.getEncoder().encodeToString(nonce), proof)
    }

    @Test
    fun `a file's AES key wrapped for a recipient only their private key can unwrap`() {
        val sender = SecureDropCrypto.generateKeyPair()
        val recipient = SecureDropCrypto.generateKeyPair()
        val (ciphertext, iv, rawKey) = SecureDropCrypto.encryptFile("the plan is at midnight".toByteArray())

        val wrappedForRecipient = SecureDropCrypto.wrapAesKeyFor(recipient.publicKeyPem, rawKey)
        val unwrapped = SecureDropCrypto.unwrapAesKey(recipient.privateKey, wrappedForRecipient)
        assertContentEquals(rawKey, unwrapped)

        // The sender's own key cannot open what was wrapped for the recipient.
        assertFailsWith<Exception> { SecureDropCrypto.unwrapAesKey(sender.privateKey, wrappedForRecipient) }

        val plain = SecureDropCrypto.decryptFile(ciphertext, iv, unwrapped)
        assertEquals("the plan is at midnight", String(plain))
    }

    @Test
    fun `a file cannot be decrypted with the wrong key`() {
        val (ciphertext, iv, _) = SecureDropCrypto.encryptFile("secret".toByteArray())
        val wrongKey = ByteArray(32)

        assertFailsWith<Exception> { SecureDropCrypto.decryptFile(ciphertext, iv, wrongKey) }
    }

    // ── sending by email, to someone with no account yet (docs/secure-drop.md) ──

    @Test
    fun `a generated password is long and drawn from an unambiguous alphabet`() {
        val password = SecureDropCrypto.generatePassword()

        assertEquals(20, password.length)
        assertTrue(password.none { it in "0O1lI" })
        assertNotEquals(password, SecureDropCrypto.generatePassword())
    }

    @Test
    fun `a file's AES key wrapped with a one-time password only opens with that password`() {
        val (ciphertext, iv, rawKey) = SecureDropCrypto.encryptFile("no account yet".toByteArray())
        val password = SecureDropCrypto.generatePassword()

        val envelope = SecureDropCrypto.wrapAesKeyWithPassword(rawKey, password)
        assertTrue(envelope.startsWith("-----BEGIN CTOS PASSWORD PROTECTED KEY-----"))

        val unwrapped = SecureDropCrypto.unwrapAesKeyWithPassword(envelope, password)
        assertContentEquals(rawKey, unwrapped)
        assertContentEquals("no account yet".toByteArray(), SecureDropCrypto.decryptFile(ciphertext, iv, unwrapped))

        assertFailsWith<IllegalArgumentException> {
            SecureDropCrypto.unwrapAesKeyWithPassword(envelope, "the wrong password")
        }
    }

    @Test
    fun `a password-protected envelope is not mistaken for an encrypted private key, or the other way round`() {
        val pair = SecureDropCrypto.generateKeyPair()
        val passphraseEnvelope = SecureDropCrypto.encryptPrivateKey(pair.privateKey, "a passphrase")
        val (_, _, rawKey) = SecureDropCrypto.encryptFile("x".toByteArray())
        val passwordEnvelope = SecureDropCrypto.wrapAesKeyWithPassword(rawKey, "a password")

        assertFailsWith<IllegalArgumentException> { SecureDropCrypto.unwrapAesKeyWithPassword(passphraseEnvelope, "a passphrase") }
        assertFailsWith<Exception> { SecureDropCrypto.decryptPrivateKey(passwordEnvelope, "a password") }
    }

    private fun extractDer(pem: String, label: String): ByteArray {
        val header = "-----BEGIN $label-----"
        val footer = "-----END $label-----"
        val body = pem.substring(pem.indexOf(header) + header.length, pem.indexOf(footer)).replace("\r", "").replace("\n", "")
        return Base64.getDecoder().decode(body)
    }
}
