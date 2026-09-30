<?php

use App\Enums\DepartmentRole;
use App\Models\Department;
use App\Models\Organization;
use App\Models\SecureDrop;
use App\Models\User;
use App\Models\UserEncryptionKey;
use App\Notifications\SecureDropReceived;
use Illuminate\Http\UploadedFile;
use Illuminate\Support\Facades\Notification;
use Illuminate\Support\Facades\Storage;

beforeEach(function () {
    Storage::fake('local');
    $this->organization = Organization::factory()->create();
    $this->department = Department::factory()->for($this->organization)->create();

    $this->sender = User::factory()->create();
    $this->department->members()->attach($this->sender->id, ['role' => DepartmentRole::Member->value]);
    UserEncryptionKey::factory()->for($this->sender)->create();

    $this->recipient = User::factory()->create();
    $this->department->members()->attach($this->recipient->id, ['role' => DepartmentRole::Member->value]);
    UserEncryptionKey::factory()->for($this->recipient)->create();
});

/** A "sending" request's payload: one ciphertext file and an encrypted key for each recipient. */
function secureDropPayload(array $recipientIds, string $name = 'secret.txt'): array
{
    return [
        'name' => $name,
        'ciphertext' => UploadedFile::fake()->createWithContent('ciphertext', random_bytes(64)),
        'iv' => base64_encode(random_bytes(12)),
        'recipients' => array_map(fn ($id) => ['user_id' => $id, 'encrypted_key' => base64_encode(random_bytes(256))], $recipientIds),
    ];
}

// ── sending one ──────────────────────────────────────────────────────────────

it('sends a file to every recipient chosen, including the sender', function () {
    $payload = secureDropPayload([$this->sender->id, $this->recipient->id]);

    $this->actingAs($this->sender)
        ->post(route('secure-drop.store', $this->department), $payload)
        ->assertRedirect(route('secure-drop.index'));

    $drop = SecureDrop::first();
    expect($drop->name)->toBe('secret.txt')
        ->and($drop->owner_id)->toBe($this->sender->id)
        ->and($drop->recipients()->count())->toBe(2)
        ->and($drop->recipients()->where('user_id', $this->recipient->id)->exists())->toBeTrue();
    Storage::disk('local')->assertExists($drop->storage_path);
});

it('emails every recipient but not the sender, with the ciphertext attached', function () {
    Notification::fake();
    $payload = secureDropPayload([$this->sender->id, $this->recipient->id]);

    $this->actingAs($this->sender)->post(route('secure-drop.store', $this->department), $payload);

    Notification::assertSentTo($this->recipient, SecureDropReceived::class, function (SecureDropReceived $notification) {
        return $notification->secureDrop->name === 'secret.txt'
            && $notification->sender->is($this->sender)
            && collect($notification->toMail($this->recipient)->rawAttachments)->isNotEmpty();
    });
    Notification::assertNotSentTo($this->sender, SecureDropReceived::class);
});

it('does not attach the ciphertext to the email when it is too large', function () {
    Notification::fake();
    $payload = secureDropPayload([$this->recipient->id]);
    $payload['ciphertext'] = UploadedFile::fake()->create('ciphertext', 11_000); // KB: just over the 10 MB cutoff

    $this->actingAs($this->sender)->post(route('secure-drop.store', $this->department), $payload);

    Notification::assertSentTo($this->recipient, SecureDropReceived::class, function (SecureDropReceived $notification) {
        return collect($notification->toMail($this->recipient)->rawAttachments)->isEmpty();
    });
});

it('answers with json when the sending page asks for it', function () {
    $payload = secureDropPayload([$this->sender->id]);

    $this->actingAs($this->sender)
        ->postJson(route('secure-drop.store', $this->department), $payload)
        ->assertOk()
        ->assertJsonStructure(['message']);
});

it('refuses to send to someone with no Secure Drop key', function () {
    $noKey = User::factory()->create();
    $this->department->members()->attach($noKey->id, ['role' => DepartmentRole::Member->value]);

    $this->actingAs($this->sender)
        ->postJson(route('secure-drop.store', $this->department), secureDropPayload([$noKey->id]))
        ->assertStatus(422);

    expect(SecureDrop::count())->toBe(0);
});

it('refuses to send to someone outside the department', function () {
    $outsider = User::factory()->create();
    UserEncryptionKey::factory()->for($outsider)->create();

    $this->actingAs($this->sender)
        ->postJson(route('secure-drop.store', $this->department), secureDropPayload([$outsider->id]))
        ->assertStatus(422);
});

it('requires the sender to have their own key set up first', function () {
    $noKeySender = User::factory()->create();
    $this->department->members()->attach($noKeySender->id, ['role' => DepartmentRole::Member->value]);

    $this->actingAs($noKeySender)
        ->get(route('secure-drop.create', $this->department))
        ->assertForbidden();

    $this->actingAs($noKeySender)
        ->post(route('secure-drop.store', $this->department), secureDropPayload([$this->recipient->id]))
        ->assertForbidden();
});

it('shows every department member, but only lets those with a Secure Drop key be picked', function () {
    $noKeyMember = User::factory()->create(['name' => 'No Key']);
    $this->department->members()->attach($noKeyMember->id, ['role' => DepartmentRole::Member->value]);

    $this->actingAs($this->sender)
        ->get(route('secure-drop.create', $this->department))
        ->assertOk()
        ->assertSee($this->recipient->name)
        ->assertSee('No Key')
        ->assertSee('No Secure Drop key yet')
        ->assertDontSee($noKeyMember->encryptionKey?->public_key ?? 'never matches');
});

it('does not let an outsider send to a department they are not in', function () {
    $outsider = User::factory()->create();
    UserEncryptionKey::factory()->for($outsider)->create();

    $this->actingAs($outsider)->get(route('secure-drop.create', $this->department))->assertForbidden();
});

// ── opening one ──────────────────────────────────────────────────────────────

it('lets the owner and every recipient read and download it, and nobody else', function () {
    $this->actingAs($this->sender)->post(route('secure-drop.store', $this->department), secureDropPayload([$this->sender->id, $this->recipient->id]));
    $drop = SecureDrop::first();
    $outsider = User::factory()->create();

    $this->actingAs($this->sender)->getJson(route('secure-drop.show', $drop))->assertOk()->assertJsonStructure(['data' => ['iv', 'encrypted_key']]);
    $this->actingAs($this->recipient)->getJson(route('secure-drop.show', $drop))->assertOk();
    $this->actingAs($outsider)->getJson(route('secure-drop.show', $drop))->assertNotFound();

    $this->actingAs($this->recipient)->get(route('secure-drop.download', $drop))->assertOk();
    $this->actingAs($outsider)->get(route('secure-drop.download', $drop))->assertNotFound();
});

it('marks a drop opened when a recipient downloads it', function () {
    $this->actingAs($this->sender)->post(route('secure-drop.store', $this->department), secureDropPayload([$this->sender->id, $this->recipient->id]));
    $drop = SecureDrop::first();

    expect($drop->recipients()->where('user_id', $this->recipient->id)->first()->opened_at)->toBeNull();

    $this->actingAs($this->recipient)->get(route('secure-drop.download', $drop));

    expect($drop->recipients()->where('user_id', $this->recipient->id)->first()->fresh()->opened_at)->not->toBeNull();
});

it('lists what was sent and what was received, separately', function () {
    $this->actingAs($this->sender)->post(route('secure-drop.store', $this->department), secureDropPayload([$this->sender->id, $this->recipient->id], 'from-sender.txt'));

    $senderPage = $this->actingAs($this->sender)->get(route('secure-drop.index'));
    $senderPage->assertSee('Sent')->assertSee('from-sender.txt');

    $recipientPage = $this->actingAs($this->recipient)->get(route('secure-drop.index'));
    $recipientPage->assertSee('Received')->assertSee('from-sender.txt');
});

// ── deleting one ─────────────────────────────────────────────────────────────

it('lets only the owner delete a drop, removing its ciphertext too', function () {
    $this->actingAs($this->sender)->post(route('secure-drop.store', $this->department), secureDropPayload([$this->sender->id, $this->recipient->id]));
    $drop = SecureDrop::first();
    $path = $drop->storage_path;

    $this->actingAs($this->recipient)->delete(route('secure-drop.destroy', $drop))->assertForbidden();
    Storage::disk('local')->assertExists($path);

    $this->actingAs($this->sender)->delete(route('secure-drop.destroy', $drop))->assertRedirect();
    Storage::disk('local')->assertMissing($path);
    expect(SecureDrop::count())->toBe(0);
});
