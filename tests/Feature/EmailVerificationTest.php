<?php

use App\Models\User;
use Illuminate\Auth\Events\Registered;
use Illuminate\Auth\Notifications\VerifyEmail;
use Illuminate\Support\Facades\Event;
use Illuminate\Support\Facades\Notification;
use Illuminate\Support\Facades\URL;

function registrationPayload(): array
{
    return [
        'name' => 'Alice',
        'email' => 'alice@example.com',
        'password' => 'correct-horse-battery',
        'password_confirmation' => 'correct-horse-battery',
    ];
}

function verificationUrl(User $user, ?string $hash = null): string
{
    return URL::temporarySignedRoute('verification.verify', now()->addMinutes(60), [
        'id' => $user->id,
        'hash' => $hash ?? sha1($user->email),
    ]);
}

it('emails a verification link after registration', function () {
    Notification::fake();

    $this->post(route('register'), registrationPayload())->assertRedirect(route('verification.notice'));

    $user = User::firstWhere('email', 'alice@example.com');
    expect($user->hasVerifiedEmail())->toBeFalse();
    Notification::assertSentTo($user, VerifyEmail::class);
});

it('still creates the account when the verification email cannot be sent', function () {
    Event::listen(Registered::class, fn () => throw new RuntimeException('SMTP is down'));

    $this->post(route('register'), registrationPayload())->assertRedirect(route('verification.notice'));

    $this->assertAuthenticated();
    expect(User::where('email', 'alice@example.com')->exists())->toBeTrue();
});

it('shows the verification notice to unverified users', function () {
    $user = User::factory()->unverified()->create();

    $this->actingAs($user)->get(route('verification.notice'))->assertOk()->assertSee($user->email);
});

it('sends verified users from the notice straight to the dashboard', function () {
    $this->actingAs(User::factory()->create())
        ->get(route('verification.notice'))
        ->assertRedirect(route('dashboard'));
});

it('keeps unverified users out of the app', function () {
    $this->actingAs(User::factory()->unverified()->create())
        ->get(route('dashboard'))
        ->assertRedirect(route('verification.notice'));
});

it('verifies the email from the signed link', function () {
    $user = User::factory()->unverified()->create();

    $this->actingAs($user)
        ->get(verificationUrl($user))
        ->assertRedirect(route('dashboard'))
        ->assertSessionHas('status');

    expect($user->fresh()->hasVerifiedEmail())->toBeTrue();
});

it('rejects a verification link with the wrong hash', function () {
    $user = User::factory()->unverified()->create();

    $this->actingAs($user)->get(verificationUrl($user, sha1('someone-else@example.com')))->assertForbidden();

    expect($user->fresh()->hasVerifiedEmail())->toBeFalse();
});

it('rejects a verification link that belongs to another user', function () {
    $owner = User::factory()->unverified()->create();
    $intruder = User::factory()->unverified()->create();

    $this->actingAs($intruder)->get(verificationUrl($owner))->assertForbidden();

    expect($owner->fresh()->hasVerifiedEmail())->toBeFalse();
});

it('rejects a verification link without a valid signature', function () {
    $user = User::factory()->unverified()->create();

    $this->actingAs($user)
        ->get(route('verification.verify', ['id' => $user->id, 'hash' => sha1($user->email)]))
        ->assertForbidden();
});

it('sends the verification email again on request', function () {
    Notification::fake();
    $user = User::factory()->unverified()->create();

    $this->actingAs($user)->post(route('verification.send'))->assertSessionHas('status');

    Notification::assertSentTo($user, VerifyEmail::class);
});

it('does not resend to an already verified user', function () {
    Notification::fake();
    $user = User::factory()->create();

    $this->actingAs($user)->post(route('verification.send'))->assertRedirect(route('dashboard'));

    Notification::assertNothingSent();
});
