<?php

use App\Models\User;
use Illuminate\Auth\Notifications\ResetPassword;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Notification;
use Illuminate\Support\Facades\Password;

const RESET_LINK_MESSAGE = 'If an account exists for that email address, we have sent a link to reset the password.';

it('shows the forgot password page and links to it from login', function () {
    $this->get(route('password.request'))->assertOk();
    $this->get(route('login'))->assertSee(route('password.request'));
});

it('emails a reset link to a registered address', function () {
    Notification::fake();
    $user = User::factory()->create();

    $this->post(route('password.email'), ['email' => $user->email])
        ->assertSessionHas('status', RESET_LINK_MESSAGE);

    Notification::assertSentTo($user, ResetPassword::class);
});

it('gives the same answer for an unknown address and sends nothing', function () {
    Notification::fake();

    $this->post(route('password.email'), ['email' => 'nobody@example.com'])
        ->assertSessionHas('status', RESET_LINK_MESSAGE)
        ->assertSessionHasNoErrors();

    Notification::assertNothingSent();
});

it('requires a valid email to request a reset link', function () {
    $this->post(route('password.email'), ['email' => 'not-an-email'])->assertSessionHasErrors('email');
});

it('shows the reset form with the token and email from the link', function () {
    $this->get(route('password.reset', ['token' => 'abc123', 'email' => 'alice@example.com']))
        ->assertOk()
        ->assertSee('abc123')
        ->assertSee('alice@example.com');
});

it('resets the password with a valid token', function () {
    $user = User::factory()->create(['password' => 'old-password-123']);
    $token = Password::createToken($user);

    $this->post(route('password.update'), [
        'token' => $token,
        'email' => $user->email,
        'password' => 'brand-new-password',
        'password_confirmation' => 'brand-new-password',
    ])->assertRedirect(route('login'))->assertSessionHas('status');

    $user->refresh();
    expect(Hash::check('brand-new-password', $user->password))->toBeTrue()
        ->and(Hash::check('old-password-123', $user->password))->toBeFalse();
});

it('signs the user out of the apps by deleting their API tokens', function () {
    $user = User::factory()->create();
    $user->createToken('laptop');
    $user->createToken('phone');
    $token = Password::createToken($user);

    $this->post(route('password.update'), [
        'token' => $token,
        'email' => $user->email,
        'password' => 'brand-new-password',
        'password_confirmation' => 'brand-new-password',
    ])->assertRedirect(route('login'));

    expect($user->tokens()->count())->toBe(0);
});

it('treats the emailed link as proof of the email address', function () {
    $user = User::factory()->unverified()->create();
    $token = Password::createToken($user);

    $this->post(route('password.update'), [
        'token' => $token,
        'email' => $user->email,
        'password' => 'brand-new-password',
        'password_confirmation' => 'brand-new-password',
    ]);

    expect($user->fresh()->hasVerifiedEmail())->toBeTrue();
});

it('rejects an invalid token and leaves the password alone', function () {
    $user = User::factory()->create(['password' => 'old-password-123']);

    $this->post(route('password.update'), [
        'token' => 'not-a-real-token',
        'email' => $user->email,
        'password' => 'brand-new-password',
        'password_confirmation' => 'brand-new-password',
    ])->assertSessionHasErrors('email');

    expect(Hash::check('old-password-123', $user->fresh()->password))->toBeTrue();
});

it('does not let a token be used twice', function () {
    $user = User::factory()->create();
    $token = Password::createToken($user);
    $payload = [
        'token' => $token,
        'email' => $user->email,
        'password' => 'brand-new-password',
        'password_confirmation' => 'brand-new-password',
    ];

    $this->post(route('password.update'), $payload)->assertRedirect(route('login'));
    $this->post(route('password.update'), [...$payload, 'password' => 'another-password-1', 'password_confirmation' => 'another-password-1'])
        ->assertSessionHasErrors('email');

    expect(Hash::check('brand-new-password', $user->fresh()->password))->toBeTrue();
});

it('rejects a weak or unconfirmed new password', function (array $password) {
    $user = User::factory()->create(['password' => 'old-password-123']);

    $this->post(route('password.update'), [
        'token' => Password::createToken($user),
        'email' => $user->email,
        ...$password,
    ])->assertSessionHasErrors('password');

    expect(Hash::check('old-password-123', $user->fresh()->password))->toBeTrue();
})->with([
    'too short' => [['password' => 'short', 'password_confirmation' => 'short']],
    'not confirmed' => [['password' => 'brand-new-password', 'password_confirmation' => 'different-password']],
]);

it('lets the user log in with the new password', function () {
    $user = User::factory()->create(['password' => 'old-password-123']);

    $this->post(route('password.update'), [
        'token' => Password::createToken($user),
        'email' => $user->email,
        'password' => 'brand-new-password',
        'password_confirmation' => 'brand-new-password',
    ]);

    $this->post(route('login'), ['email' => $user->email, 'password' => 'old-password-123'])->assertSessionHasErrors('email');
    $this->post(route('login'), ['email' => $user->email, 'password' => 'brand-new-password'])->assertRedirect(route('dashboard'));
});
