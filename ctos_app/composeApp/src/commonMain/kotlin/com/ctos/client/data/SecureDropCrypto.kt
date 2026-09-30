package com.ctos.client.data

/**
 * Secure Drop's cryptography (docs/secure-drop.md): RSA-2048 key pairs, encrypting a private key
 * with a passphrase, and encrypting a file with a one-time AES-256 key wrapped for each
 * recipient. The desktop and Android apps both implement it with `javax.crypto`/`java.security`
 * (see the `actual` in `commonJvmMain`, shared by both the same way `localFiles()` already is);
 * the website's browser speaks the exact same protocol with Web Crypto
 * (`resources/js/secure-drop-crypto.js`).
 *
 * A private key only ever exists as this opaque handle outside the platform code that made it —
 * never as bytes a screen or a view model could accidentally log or store.
 */
expect class SecureDropPrivateKey

data class SecureDropKeyPair(val publicKeyPem: String, val privateKey: SecureDropPrivateKey)

data class SecureDropEncryptedFile(val ciphertext: ByteArray, val iv: String, val rawKey: ByteArray)

expect object SecureDropCrypto {
    fun generateKeyPair(): SecureDropKeyPair

    /** For someone bringing their own existing RSA key pair, both PEMs plain. */
    fun importKeyPair(publicKeyPem: String, privateKeyPem: String): SecureDropKeyPair

    /** sha256 of the public key's DER, hex — matches the server's own fingerprint of the same key. */
    fun fingerprintOfPublicKeyPem(publicKeyPem: String): String

    fun encryptPrivateKey(privateKey: SecureDropPrivateKey, passphrase: String): String

    /** Throws (a message meant to be shown as-is) when the passphrase is wrong. */
    fun decryptPrivateKey(envelopePem: String, passphrase: String): SecureDropPrivateKey

    /** Step two of proving possession of a key pair: decrypts the server's challenge. */
    fun decryptChallenge(privateKey: SecureDropPrivateKey, ciphertextBase64: String): String

    fun wrapAesKeyFor(publicKeyPem: String, rawAesKey: ByteArray): String

    fun unwrapAesKey(privateKey: SecureDropPrivateKey, encryptedKeyBase64: String): ByteArray

    fun encryptFile(fileBytes: ByteArray): SecureDropEncryptedFile

    fun decryptFile(ciphertext: ByteArray, ivBase64: String, rawAesKey: ByteArray): ByteArray
}
