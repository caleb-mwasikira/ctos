<?php

use App\Models\SecureDrop;
use App\Models\SecureDropRecipient;
use App\Models\User;
use App\Models\UserEncryptionKey;
use App\Notifications\SecureDropReceived;
use App\Notifications\SecureDropSharedByPassword;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Notification;
use Illuminate\Support\Facades\Storage;

beforeEach(function () {
    Storage::fake('local');
    $this->sender = User::factory()->create();
    UserEncryptionKey::factory()->for($this->sender)->create();
});

/** A "sending directly" request's payload: not scoped to any department. */
function sendByEmailPayload(array $ownRecipient, ?array $passwordRecipient = null, string $name = 'secret.txt'): array
{
    $payload = [
        'name' => $name,
        'ciphertext' => UploadedFile::fake()->createWithContent('ciphertext', random_bytes(64)),
        'iv' => base64_encode(random_bytes(12)),
        'recipients' => [$ownRecipient],
    ];

    if ($passwordRecipient !== null) {
        $payload['password_recipient'] = $passwordRecipient;
        $payload['password'] = 'a-very-good-one-time-password';
    }

    return $payload;
}

// ── looking someone up ──────────────────────────────────────────────────────

it('finds an account with a Secure Drop key by email', function () {
    $recipient = User::factory()->create();
    UserEncryptionKey::factory()->for($recipient)->create();

    $this->actingAs($this->sender)
        ->postJson(route('secure-drop.lookup'), ['email' => $recipient->email])
        ->assertOk()
        ->assertJson(['data' => ['has_account' => true, 'id' => $recipient->id]])
        ->assertJsonStructure(['data' => ['public_key', 'fingerprint']]);
});

it('says an email has no account when there is none, or none with a key yet', function () {
    $this->actingAs($this->sender)
        ->postJson(route('secure-drop.lookup'), ['email' => 'nobody@example.com'])
        ->assertOk()
        ->assertJson(['data' => ['has_account' => false]]);

    $noKey = User::factory()->create();
    $this->actingAs($this->sender)
        ->postJson(route('secure-drop.lookup'), ['email' => $noKey->email])
        ->assertOk()
        ->assertJson(['data' => ['has_account' => false]]);
});

it('refuses to look up your own email', function () {
    $this->actingAs($this->sender)
        ->postJson(route('secure-drop.lookup'), ['email' => $this->sender->email])
        ->assertStatus(422);
});

// ── sending to an existing account, not scoped to a department ─────────────

it('sends directly to an account by email, with no department at all', function () {
    Notification::fake();
    $recipient = User::factory()->create();
    UserEncryptionKey::factory()->for($recipient)->create();

    $payload = sendByEmailPayload(['user_id' => $this->sender->id, 'encrypted_key' => base64_encode(random_bytes(256))]);
    $payload['recipients'][] = ['user_id' => $recipient->id, 'encrypted_key' => base64_encode(random_bytes(256))];

    $this->actingAs($this->sender)->post(route('secure-drop.send'), $payload)->assertRedirect(route('secure-drop.index'));

    $drop = SecureDrop::first();
    expect($drop->department_id)->toBeNull()
        ->and($drop->owner_id)->toBe($this->sender->id)
        ->and($drop->recipients()->where('user_id', $recipient->id)->exists())->toBeTrue();
    Notification::assertSentTo($recipient, SecureDropReceived::class);
});

it('refuses a recipient with no Secure Drop key', function () {
    $noKey = User::factory()->create();
    $payload = sendByEmailPayload(['user_id' => $this->sender->id, 'encrypted_key' => base64_encode(random_bytes(256))]);
    $payload['recipients'][] = ['user_id' => $noKey->id, 'encrypted_key' => base64_encode(random_bytes(256))];

    $this->actingAs($this->sender)->postJson(route('secure-drop.send'), $payload)->assertStatus(422);
    expect(SecureDrop::count())->toBe(0);
});

// ── sending to someone with no account yet ──────────────────────────────────

it('protects it with a one-time password and emails that, a link, and an invitation, when there is no account', function () {
    Notification::fake();
    $payload = sendByEmailPayload(
        ['user_id' => $this->sender->id, 'encrypted_key' => base64_encode(random_bytes(256))],
        ['email' => 'newperson@example.com', 'encrypted_key' => base64_encode(random_bytes(96))],
    );

    $this->actingAs($this->sender)->post(route('secure-drop.send'), $payload)->assertRedirect(route('secure-drop.index'));

    $drop = SecureDrop::first();
    $recipient = $drop->recipients()->whereNull('user_id')->first();
    expect($recipient)->not->toBeNull()
        ->and($recipient->email)->toBe('newperson@example.com')
        ->and($recipient->access_token)->not->toBeNull()
        ->and($recipient->isPasswordProtected())->toBeTrue();

    Notification::assertSentOnDemand(
        SecureDropSharedByPassword::class,
        function (SecureDropSharedByPassword $notification, array $channels, object $notifiable) {
            return $notifiable->routes['mail'] === 'newperson@example.com'
                && $notification->password === 'a-very-good-one-time-password'
                && $notification->token === $notification->secureDrop->recipients()->whereNull('user_id')->first()->access_token;
        },
    );
});

it('never stores the one-time password itself', function () {
    Notification::fake();
    $payload = sendByEmailPayload(
        ['user_id' => $this->sender->id, 'encrypted_key' => base64_encode(random_bytes(256))],
        ['email' => 'newperson@example.com', 'encrypted_key' => base64_encode(random_bytes(96))],
    );

    $this->actingAs($this->sender)->post(route('secure-drop.send'), $payload);

    $recipient = SecureDropRecipient::whereNull('user_id')->first();
    expect($recipient->encrypted_key)->not->toContain('a-very-good-one-time-password');
});

// ── opening one sent to someone with no account ─────────────────────────────

it('lets anyone with the link open the page, and see (only) what it needs to decrypt', function () {
    Notification::fake();
    $payload = sendByEmailPayload(
        ['user_id' => $this->sender->id, 'encrypted_key' => base64_encode(random_bytes(256))],
        ['email' => 'newperson@example.com', 'encrypted_key' => 'a-password-wrapped-key'],
    );
    $this->actingAs($this->sender)->post(route('secure-drop.send'), $payload);
    $recipient = SecureDropRecipient::whereNull('user_id')->first();

    $this->get(route('secure-drop.shared.show', $recipient->access_token))->assertOk()->assertSee('secret.txt');

    $this->getJson(route('secure-drop.shared.meta', $recipient->access_token))
        ->assertOk()
        ->assertJson(['data' => ['name' => 'secret.txt', 'encrypted_key' => 'a-password-wrapped-key']]);

    $this->get(route('secure-drop.shared.download', $recipient->access_token))->assertOk();
});

it('marks it opened, and 404s for a token that does not exist', function () {
    Notification::fake();
    $payload = sendByEmailPayload(
        ['user_id' => $this->sender->id, 'encrypted_key' => base64_encode(random_bytes(256))],
        ['email' => 'newperson@example.com', 'encrypted_key' => 'a-password-wrapped-key'],
    );
    $this->actingAs($this->sender)->post(route('secure-drop.send'), $payload);
    $recipient = SecureDropRecipient::whereNull('user_id')->first();

    expect($recipient->opened_at)->toBeNull();
    $this->get(route('secure-drop.shared.download', $recipient->access_token));
    expect($recipient->fresh()->opened_at)->not->toBeNull();

    $this->get(route('secure-drop.shared.show', 'not-a-real-token'))->assertNotFound();
});

// ── showing up on the index page ────────────────────────────────────────────

it('shows a department-less drop as "Sent by email" instead of a department name', function () {
    $recipient = User::factory()->create();
    UserEncryptionKey::factory()->for($recipient)->create();
    $payload = sendByEmailPayload(['user_id' => $this->sender->id, 'encrypted_key' => base64_encode(random_bytes(256))]);
    $payload['recipients'][] = ['user_id' => $recipient->id, 'encrypted_key' => base64_encode(random_bytes(256))];
    $this->actingAs($this->sender)->post(route('secure-drop.send'), $payload);

    $this->actingAs($this->sender)->get(route('secure-drop.index'))->assertOk()->assertSee('Sent by email');
});
