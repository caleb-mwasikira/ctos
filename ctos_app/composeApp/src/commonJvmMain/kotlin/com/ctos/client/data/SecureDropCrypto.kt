package com.ctos.client.data

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.spec.MGF1ParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.text.Normalizer
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.OAEPParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PSource
import javax.crypto.spec.SecretKeySpec

/**
 * The desktop and Android implementation of [SecureDropCrypto] (docs/secure-drop.md), shared by
 * both the same way `localFiles()` already is (see `commonMain/SecureDropCrypto.kt`'s own doc
 * comment): `javax.crypto`/`java.security`, which both platforms have.
 *
 * RSA-OAEP uses SHA-1 (only for OAEP's own padding, not a signature — this is not the weakness
 * SHA-1 has elsewhere): the website's PHP encrypts a key pair's very first challenge with
 * openssl_public_encrypt(), whose simple OPENSSL_PKCS1_OAEP_PADDING has no way to ask for a
 * different hash, so every device speaking this protocol has to agree on the one PHP is stuck
 * with.
 */
actual class SecureDropPrivateKey internal constructor(internal val key: PrivateKey)

actual object SecureDropCrypto {
    private const val PBKDF2_ITERATIONS = 210_000 // OWASP's 2023 minimum for PBKDF2-SHA256
    private const val ENVELOPE_VERSION: Byte = 1
    private val oaep = OAEPParameterSpec("SHA-1", "MGF1", MGF1ParameterSpec.SHA1, PSource.PSpecified.DEFAULT)
    private val random = SecureRandom()

    // ── PEM framing ──────────────────────────────────────────────────────────

    private fun wrapPem(label: String, bytes: ByteArray): String {
        val base64 = Base64.getEncoder().encodeToString(bytes)
        return "-----BEGIN $label-----\n${base64.chunked(64).joinToString("\n")}\n-----END $label-----"
    }

    private fun unwrapPem(pem: String, label: String): ByteArray {
        val header = "-----BEGIN $label-----"
        val footer = "-----END $label-----"
        val start = pem.indexOf(header)
        val end = pem.indexOf(footer)
        require(start != -1 && end != -1) { "Expected a \"$header\" PEM." }
        val body = pem.substring(start + header.length, end).replace("\r", "").replace("\n", "")
        return Base64.getDecoder().decode(body)
    }

    private fun publicKeyOf(pem: String) =
        KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(unwrapPem(pem, "PUBLIC KEY")))

    // ── the RSA-OAEP key pair (encrypt/decrypt only; it never signs anything) ─

    actual fun generateKeyPair(): SecureDropKeyPair {
        val generator = KeyPairGenerator.getInstance("RSA")
        generator.initialize(2048, random)
        val pair = generator.generateKeyPair()
        return SecureDropKeyPair(wrapPem("PUBLIC KEY", pair.public.encoded), SecureDropPrivateKey(pair.private))
    }

    actual fun importKeyPair(publicKeyPem: String, privateKeyPem: String): SecureDropKeyPair {
        val privateKey = KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(unwrapPem(privateKeyPem, "PRIVATE KEY")))
        return SecureDropKeyPair(publicKeyPem, SecureDropPrivateKey(privateKey))
    }

    actual fun fingerprintOfPublicKeyPem(publicKeyPem: String): String {
        val der = unwrapPem(publicKeyPem, "PUBLIC KEY")
        return MessageDigest.getInstance("SHA-256").digest(der).joinToString("") { "%02x".format(it) }
    }

    // ── the passphrase-protected envelope (a private key, or someone's copy of a file's AES key) ──
    //
    // The same PBKDF2 + AES-GCM envelope wraps two different things under two different PEM
    // labels: a private key itself (someone's own Secure Drop key, protected with their
    // passphrase) and, for someone with no account yet, one file's AES key (protected with a
    // one-time password instead — see docs/secure-drop.md's "sending by email").

    /**
     * NFC-normalized first: the same passphrase can arrive as different Unicode code point
     * sequences depending on where it was typed (an accented letter composed via a Linux/AWT dead
     * key can come in decomposed — a base letter plus a combining mark — while a browser's own
     * input handling normally composes it), and those encode to different bytes. Both this and
     * secure-drop-crypto.js's deriveAesKeyFromPassphrase() normalize, so an envelope made on one
     * platform still opens with "the same" passphrase typed on the other.
     */
    private fun deriveAesKeyFromPassphrase(passphrase: String, salt: ByteArray, iterations: Int): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val normalized = Normalizer.normalize(passphrase, Normalizer.Form.NFC)
        val spec = PBEKeySpec(normalized.toCharArray(), salt, iterations, 256)
        return SecretKeySpec(factory.generateSecret(spec).encoded, "AES")
    }

    private fun encryptBytesWithPassphrase(label: String, plainBytes: ByteArray, passphrase: String): String {
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val aesKey = deriveAesKeyFromPassphrase(passphrase, salt, PBKDF2_ITERATIONS)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(plainBytes)

        val envelope = ByteArray(1 + salt.size + 4 + iv.size + ciphertext.size)
        var offset = 0
        envelope[offset++] = ENVELOPE_VERSION
        salt.copyInto(envelope, offset); offset += salt.size
        envelope[offset++] = (PBKDF2_ITERATIONS ushr 24).toByte()
        envelope[offset++] = (PBKDF2_ITERATIONS ushr 16).toByte()
        envelope[offset++] = (PBKDF2_ITERATIONS ushr 8).toByte()
        envelope[offset++] = PBKDF2_ITERATIONS.toByte()
        iv.copyInto(envelope, offset); offset += iv.size
        ciphertext.copyInto(envelope, offset)

        return wrapPem(label, envelope)
    }

    /** Throws (a message meant to be shown as-is) when the passphrase is wrong — AES-GCM's own tag check fails first. */
    private fun decryptBytesWithPassphrase(label: String, envelopePem: String, passphrase: String, wrongPassphraseMessage: String): ByteArray {
        val envelope = unwrapPem(envelopePem, label)
        require(envelope.size >= 1 + 16 + 4 + 12 + 16 && envelope[0] == ENVELOPE_VERSION) { "That does not look like a Secure Drop encrypted key." }

        var offset = 1
        val salt = envelope.copyOfRange(offset, offset + 16); offset += 16
        val iterations = ((envelope[offset].toInt() and 0xFF) shl 24) or
            ((envelope[offset + 1].toInt() and 0xFF) shl 16) or
            ((envelope[offset + 2].toInt() and 0xFF) shl 8) or
            (envelope[offset + 3].toInt() and 0xFF)
        offset += 4
        val iv = envelope.copyOfRange(offset, offset + 12); offset += 12
        val ciphertext = envelope.copyOfRange(offset, envelope.size)

        val aesKey = deriveAesKeyFromPassphrase(passphrase, salt, iterations)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, aesKey, GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext)
        } catch (e: Exception) {
            throw IllegalArgumentException(wrongPassphraseMessage)
        }
    }

    actual fun encryptPrivateKey(privateKey: SecureDropPrivateKey, passphrase: String): String {
        val plainDer = privateKey.key.encoded // PKCS8, the JCA default for an RSA PrivateKey
        return encryptBytesWithPassphrase("CTOS ENCRYPTED PRIVATE KEY", plainDer, passphrase)
    }

    actual fun decryptPrivateKey(envelopePem: String, passphrase: String): SecureDropPrivateKey {
        val plainDer = decryptBytesWithPassphrase("CTOS ENCRYPTED PRIVATE KEY", envelopePem, passphrase, "Wrong passphrase.")
        return SecureDropPrivateKey(KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(plainDer)))
    }

    actual fun wrapAesKeyWithPassword(rawAesKey: ByteArray, password: String): String =
        encryptBytesWithPassphrase("CTOS PASSWORD PROTECTED KEY", rawAesKey, password)

    actual fun unwrapAesKeyWithPassword(envelopePem: String, password: String): ByteArray =
        decryptBytesWithPassphrase("CTOS PASSWORD PROTECTED KEY", envelopePem, password, "Wrong password.")

    private const val PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789" // no 0/O/1/l/I

    actual fun generatePassword(): String {
        val bytes = ByteArray(20).also(random::nextBytes)
        return bytes.map { PASSWORD_ALPHABET[(it.toInt() and 0xFF) % PASSWORD_ALPHABET.length] }.joinToString("")
    }

    // ── proving possession of the private key (docs/secure-drop.md) ─────────

    actual fun decryptChallenge(privateKey: SecureDropPrivateKey, ciphertextBase64: String): String {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey.key, oaep)
        val nonce = cipher.doFinal(Base64.getDecoder().decode(ciphertextBase64))
        return Base64.getEncoder().encodeToString(nonce)
    }

    // ── wrapping and unwrapping a file's AES key for one recipient's public key ─

    actual fun wrapAesKeyFor(publicKeyPem: String, rawAesKey: ByteArray): String {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding")
        cipher.init(Cipher.ENCRYPT_MODE, publicKeyOf(publicKeyPem), oaep)
        return Base64.getEncoder().encodeToString(cipher.doFinal(rawAesKey))
    }

    actual fun unwrapAesKey(privateKey: SecureDropPrivateKey, encryptedKeyBase64: String): ByteArray {
        val cipher = Cipher.getInstance("RSA/ECB/OAEPWithSHA-1AndMGF1Padding")
        cipher.init(Cipher.DECRYPT_MODE, privateKey.key, oaep)
        return cipher.doFinal(Base64.getDecoder().decode(encryptedKeyBase64))
    }

    // ── encrypting and decrypting a file with its own one-time AES-256 key ──

    actual fun encryptFile(fileBytes: ByteArray): SecureDropEncryptedFile {
        val keyBytes = ByteArray(32).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
        val ciphertext = cipher.doFinal(fileBytes)
        return SecureDropEncryptedFile(ciphertext, Base64.getEncoder().encodeToString(iv), keyBytes)
    }

    actual fun decryptFile(ciphertext: ByteArray, ivBase64: String, rawAesKey: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(rawAesKey, "AES"), GCMParameterSpec(128, Base64.getDecoder().decode(ivBase64)))
        return cipher.doFinal(ciphertext)
    }
}
