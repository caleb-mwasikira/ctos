<?php

namespace App\Support;

use RuntimeException;

/**
 * Checking the two PEMs of a Secure Drop key pair (see docs/secure-drop.md), and the one bit of
 * RSA this application itself ever does: encrypting a proof-of-possession nonce with someone's
 * public key. It never decrypts anything — that would need the private key, which it never has.
 */
class SecureDropKey
{
    private const ENCRYPTED_PRIVATE_KEY_HEADER = '-----BEGIN CTOS ENCRYPTED PRIVATE KEY-----';

    private const ENCRYPTED_PRIVATE_KEY_FOOTER = '-----END CTOS ENCRYPTED PRIVATE KEY-----';

    /** The smallest an envelope could legitimately be: version + salt + iterations + iv + a 16-byte GCM tag on an empty plaintext. */
    private const MIN_ENVELOPE_BYTES = 1 + 16 + 4 + 12 + 16;

    /**
     * Null when $pem is a well-formed RSA public key PEM of at least 2048 bits; a message to show
     * the person otherwise.
     */
    public static function checkPublicKey(string $pem): ?string
    {
        if (! str_contains($pem, '-----BEGIN PUBLIC KEY-----')) {
            return 'That does not look like a public key (expected a "-----BEGIN PUBLIC KEY-----" PEM).';
        }

        $key = @openssl_pkey_get_public($pem);
        if ($key === false) {
            return 'That public key could not be read. Check it was copied in full.';
        }

        $details = openssl_pkey_get_details($key);
        if (($details['type'] ?? null) !== OPENSSL_KEYTYPE_RSA) {
            return 'Secure Drop keys must be RSA keys.';
        }
        if (($details['bits'] ?? 0) < 2048) {
            return 'Secure Drop keys must be at least 2048 bits.';
        }

        return null;
    }

    /**
     * Null when $pem is shaped like one of our encrypted private key envelopes — framed right, and
     * long enough to actually hold an encrypted key rather than, say, an empty string. This is a
     * structural check only: there is no way to confirm what is inside without the passphrase,
     * which this server never has.
     */
    public static function checkEncryptedPrivateKey(string $pem): ?string
    {
        $pem = trim($pem);

        if (! str_starts_with($pem, self::ENCRYPTED_PRIVATE_KEY_HEADER) || ! str_ends_with($pem, self::ENCRYPTED_PRIVATE_KEY_FOOTER)) {
            return 'That does not look like an encrypted private key (expected a "-----BEGIN CTOS ENCRYPTED PRIVATE KEY-----" PEM). '
                .'Make sure it was encrypted with a passphrase on your own device before it was sent here.';
        }

        $body = trim(substr(
            $pem,
            strlen(self::ENCRYPTED_PRIVATE_KEY_HEADER),
            strlen($pem) - strlen(self::ENCRYPTED_PRIVATE_KEY_HEADER) - strlen(self::ENCRYPTED_PRIVATE_KEY_FOOTER)
        ));

        $bytes = base64_decode(str_replace(["\r", "\n"], '', $body), strict: true);
        if ($bytes === false) {
            return 'That encrypted private key is not valid base64.';
        }
        if (strlen($bytes) < self::MIN_ENVELOPE_BYTES) {
            return 'That encrypted private key is too short to be real.';
        }
        if (ord($bytes[0]) !== 1) {
            return 'That encrypted private key uses a format this server does not recognise.';
        }

        // A real private key must never reach here unencrypted — including as the "ciphertext" of
        // an otherwise well-shaped envelope, in case the passphrase step was skipped by mistake.
        // (openssl_pkey_get_private() would not recognise our own header, so the check has to be
        // against what the envelope's ciphertext field would be if it were, itself, a plain key.)
        $ciphertext = substr($bytes, 1 + 16 + 4 + 12);
        $asPlainKeyPem = "-----BEGIN PRIVATE KEY-----\n".chunk_split(base64_encode($ciphertext), 64).'-----END PRIVATE KEY-----';
        if (@openssl_pkey_get_private($asPlainKeyPem) !== false) {
            return 'That private key is not encrypted. Encrypt it with a passphrase before uploading it.';
        }

        return null;
    }

    /** sha256 of the public key's DER bytes, hex — an SSH-style fingerprint to show people. */
    public static function fingerprint(string $publicKeyPem): string
    {
        return hash('sha256', self::derOf($publicKeyPem));
    }

    /** Encrypts $nonce (raw bytes) with the given public key (RSA-OAEP/SHA-256), base64. */
    public static function encryptNonce(string $publicKeyPem, string $nonce): string
    {
        $key = openssl_pkey_get_public($publicKeyPem);
        if ($key === false) {
            throw new RuntimeException('Not a valid public key.');
        }

        if (! openssl_public_encrypt($nonce, $encrypted, $key, OPENSSL_PKCS1_OAEP_PADDING)) {
            throw new RuntimeException('Could not encrypt the challenge nonce.');
        }

        return base64_encode($encrypted);
    }

    /** The DER bytes underneath a PEM's "-----BEGIN ...-----" / "-----END ...-----" framing. */
    private static function derOf(string $pem): string
    {
        preg_match('/-----BEGIN [^-]+-----(.*)-----END [^-]+-----/s', $pem, $matches);
        $body = str_replace(["\r", "\n"], '', trim($matches[1] ?? ''));

        return base64_decode($body) ?: '';
    }
}
