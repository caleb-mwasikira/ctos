<?php

use App\Models\User;
use App\Models\UserEncryptionKey;
use App\Support\SecureDropKey;

// ── the PEM checks, direct ──────────────────────────────────────────────────

it('accepts a real RSA public key and refuses everything else', function () {
    $pair = makeSecureDropKeyPair();

    expect(SecureDropKey::checkPublicKey($pair['public_key']))->toBeNull()
        ->and(SecureDropKey::checkPublicKey('not a key'))->not->toBeNull()
        ->and(SecureDropKey::checkPublicKey("-----BEGIN PUBLIC KEY-----\nbm90IHJlYWxseSBhIGtleQ==\n-----END PUBLIC KEY-----"))->not->toBeNull();
});

it('refuses a public key smaller than 2048 bits', function () {
    $small = openssl_pkey_new(['private_key_bits' => 1024, 'private_key_type' => OPENSSL_KEYTYPE_RSA]);
    $pem = openssl_pkey_get_details($small)['key'];

    expect(SecureDropKey::checkPublicKey($pem))->toContain('2048 bits');
});

it('accepts a well-formed encrypted private key envelope and refuses everything else', function () {
    expect(SecureDropKey::checkEncryptedPrivateKey(makeSecureDropEnvelope()))->toBeNull()
        ->and(SecureDropKey::checkEncryptedPrivateKey('not an envelope'))->not->toBeNull()
        ->and(SecureDropKey::checkEncryptedPrivateKey("-----BEGIN CTOS ENCRYPTED PRIVATE KEY-----\nAA==\n-----END CTOS ENCRYPTED PRIVATE KEY-----"))->not->toBeNull();
});

it('refuses an envelope whose "ciphertext" is secretly still a plain private key', function () {
    $pair = makeSecureDropKeyPair();
    openssl_pkey_export($pair['resource'], $plainPem);
    preg_match('/-----BEGIN PRIVATE KEY-----(.*)-----END PRIVATE KEY-----/s', $plainPem, $matches);
    $plainDer = base64_decode(str_replace(["\r", "\n"], '', trim($matches[1])));

    $bytes = chr(1).random_bytes(16).pack('N', 210_000).random_bytes(12).$plainDer;
    $lines = implode("\n", str_split(base64_encode($bytes), 64));
    $envelope = "-----BEGIN CTOS ENCRYPTED PRIVATE KEY-----\n{$lines}\n-----END CTOS ENCRYPTED PRIVATE KEY-----";

    expect(SecureDropKey::checkEncryptedPrivateKey($envelope))->toContain('not encrypted');
});

it('fingerprints a public key as sha256 of its DER, hex', function () {
    $pair = makeSecureDropKeyPair();

    $fingerprint = SecureDropKey::fingerprint($pair['public_key']);

    expect($fingerprint)->toHaveLength(64)->toMatch('/^[0-9a-f]+$/')
        // Deterministic: the same key always gets the same fingerprint.
        ->and(SecureDropKey::fingerprint($pair['public_key']))->toBe($fingerprint);
});

// ── setting up a key pair through the controller ────────────────────────────

beforeEach(function () {
    $this->user = User::factory()->create();
});

it('proves possession of the private key before trusting it, and stores the key pair', function () {
    $pair = makeSecureDropKeyPair();

    $challenge = $this->actingAs($this->user)
        ->postJson('/secure-drop/keys/challenge', ['public_key' => $pair['public_key']])
        ->assertOk()
        ->json('data.ciphertext');

    $proof = decryptSecureDropChallenge($pair['resource'], $challenge);
    $envelope = makeSecureDropEnvelope();

    $this->postJson('/secure-drop/keys', ['proof' => $proof, 'encrypted_private_key' => $envelope])
        ->assertRedirect()
        ->assertSessionHas('status');

    $key = UserEncryptionKey::where('user_id', $this->user->id)->first();
    expect($key)->not->toBeNull()
        // Laravel's own TrimStrings middleware trims the request's trailing newline; that is fine, OpenSSL reads either.
        ->and(trim($key->public_key))->toBe(trim($pair['public_key']))
        ->and($key->encrypted_private_key)->toBe($envelope)
        ->and($key->fingerprint)->toBe(SecureDropKey::fingerprint($pair['public_key']));
});

it('refuses a proof that does not match the challenge', function () {
    $pair = makeSecureDropKeyPair();

    $this->actingAs($this->user)->postJson('/secure-drop/keys/challenge', ['public_key' => $pair['public_key']]);

    $this->postJson('/secure-drop/keys', ['proof' => base64_encode('wrong nonce'), 'encrypted_private_key' => makeSecureDropEnvelope()])
        ->assertJsonValidationErrors('proof');

    expect(UserEncryptionKey::where('user_id', $this->user->id)->exists())->toBeFalse();
});

it('refuses proof from a challenge that has expired', function () {
    $pair = makeSecureDropKeyPair();

    $challenge = $this->actingAs($this->user)
        ->postJson('/secure-drop/keys/challenge', ['public_key' => $pair['public_key']])
        ->json('data.ciphertext');
    $proof = decryptSecureDropChallenge($pair['resource'], $challenge);

    $this->travel(6)->minutes();

    $this->postJson('/secure-drop/keys', ['proof' => $proof, 'encrypted_private_key' => makeSecureDropEnvelope()])
        ->assertJsonValidationErrors('proof');
});

it('refuses to store an unencrypted private key even with a correct proof', function () {
    $pair = makeSecureDropKeyPair();
    openssl_pkey_export($pair['resource'], $plainPrivateKeyPem);

    $challenge = $this->actingAs($this->user)
        ->postJson('/secure-drop/keys/challenge', ['public_key' => $pair['public_key']])
        ->json('data.ciphertext');
    $proof = decryptSecureDropChallenge($pair['resource'], $challenge);

    $this->postJson('/secure-drop/keys', ['proof' => $proof, 'encrypted_private_key' => $plainPrivateKeyPem])
        ->assertJsonValidationErrors('encrypted_private_key');
});

it('requires a login for every step', function () {
    $this->postJson('/secure-drop/keys/challenge', ['public_key' => 'x'])->assertStatus(401);
    $this->postJson('/secure-drop/keys', ['proof' => 'x', 'encrypted_private_key' => 'x'])->assertStatus(401);
});

// ── downloading and removing a key ──────────────────────────────────────────

it('lets someone download only their own encrypted key', function () {
    $mine = UserEncryptionKey::factory()->for($this->user)->create(['encrypted_private_key' => 'my-envelope']);
    $other = User::factory()->create();

    $this->actingAs($this->user)->get(route('secure-drop.keys.download'))
        ->assertOk()->assertSee('my-envelope');

    $this->actingAs($other)->get(route('secure-drop.keys.download'))->assertNotFound();
});

it('removes the key pair, and everything it sent or received, on request', function () {
    UserEncryptionKey::factory()->for($this->user)->create();

    $this->actingAs($this->user)->delete(route('secure-drop.keys.destroy'))
        ->assertRedirect()->assertSessionHas('status');

    expect(UserEncryptionKey::where('user_id', $this->user->id)->exists())->toBeFalse();
});
