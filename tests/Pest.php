<?php

use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

/*
|--------------------------------------------------------------------------
| Test Case
|--------------------------------------------------------------------------
|
| The closure you provide to your test functions is always bound to a specific PHPUnit test
| case class. By default, that class is "PHPUnit\Framework\TestCase". Of course, you may
| need to change it using the "pest()" function to bind different classes or traits.
|
*/

pest()->extend(TestCase::class)
    ->use(RefreshDatabase::class)
    ->in('Feature');

/*
|--------------------------------------------------------------------------
| Expectations
|--------------------------------------------------------------------------
|
| When you're writing tests, you often need to check that values meet certain conditions. The
| "expect()" function gives you access to a set of "expectations" methods that you can use
| to assert different things. Of course, you may extend the Expectation API at any time.
|
*/

expect()->extend('toBeOne', function () {
    return $this->toBe(1);
});

/*
|--------------------------------------------------------------------------
| Functions
|--------------------------------------------------------------------------
|
| While Pest is very powerful out-of-the-box, you may have some testing code specific to your
| project that you don't want to repeat in every file. Here you can also expose helpers as
| global functions to help you to reduce the number of lines of code in your test files.
|
*/

/**
 * A real RSA-2048 key pair, the same shape a browser's Web Crypto would hand the server: the
 * public key as a PEM, and the OpenSSL resource for the test itself to decrypt a challenge with
 * (standing in for what the person's own device does in resources/js/secure-drop-crypto.js).
 */
function makeSecureDropKeyPair(): array
{
    $key = openssl_pkey_new(['private_key_bits' => 2048, 'private_key_type' => OPENSSL_KEYTYPE_RSA]);
    $details = openssl_pkey_get_details($key);

    return ['resource' => $key, 'public_key' => $details['key']];
}

/** What the person's device sends back after decrypting the challenge (see EncryptionKeyController::challenge). */
function decryptSecureDropChallenge(mixed $privateKeyResource, string $ciphertextBase64): string
{
    openssl_private_decrypt(base64_decode($ciphertextBase64), $nonce, $privateKeyResource, OPENSSL_PKCS1_OAEP_PADDING);

    return base64_encode($nonce);
}

/**
 * A structurally valid "-----BEGIN CTOS ENCRYPTED PRIVATE KEY-----" envelope (see
 * docs/secure-drop.md): real random bytes in the right shape, but not an actual encrypted key —
 * good enough for anything that only checks the envelope is well-formed and refuses to decrypt.
 */
function makeSecureDropEnvelope(): string
{
    $bytes = chr(1).random_bytes(16).pack('N', 210_000).random_bytes(12).random_bytes(48);
    $lines = implode("\n", str_split(base64_encode($bytes), 64));

    return "-----BEGIN CTOS ENCRYPTED PRIVATE KEY-----\n{$lines}\n-----END CTOS ENCRYPTED PRIVATE KEY-----";
}
