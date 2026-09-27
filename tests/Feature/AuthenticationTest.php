<?php

use App\Models\User;

it('shows the public pages to guests', function (string $route) {
    $this->get(route($route))->assertOk();
})->with(['home', 'how-it-works', 'downloads', 'login', 'register']);

it('registers a new user and logs them in', function () {
    $this->post(route('register'), [
        'name' => 'Alice',
        'email' => 'alice@example.com',
        'password' => 'correct-horse-battery',
        'password_confirmation' => 'correct-horse-battery',
    ])->assertRedirect(route('verification.notice'));

    $this->assertAuthenticated();
    expect(User::where('email', 'alice@example.com')->exists())->toBeTrue();
});

it('rejects registration with a duplicate email', function () {
    User::factory()->create(['email' => 'alice@example.com']);

    $this->post(route('register'), [
        'name' => 'Alice',
        'email' => 'alice@example.com',
        'password' => 'correct-horse-battery',
        'password_confirmation' => 'correct-horse-battery',
    ])->assertSessionHasErrors('email');

    $this->assertGuest();
});

it('logs in with valid credentials', function () {
    $user = User::factory()->create(['password' => 'secret-password']);

    $this->post(route('login'), ['email' => $user->email, 'password' => 'secret-password'])
        ->assertRedirect(route('dashboard'));

    $this->assertAuthenticatedAs($user);
});

it('rejects invalid credentials', function () {
    $user = User::factory()->create(['password' => 'secret-password']);

    $this->post(route('login'), ['email' => $user->email, 'password' => 'wrong'])
        ->assertSessionHasErrors('email');

    $this->assertGuest();
});

it('logs out', function () {
    $this->actingAs(User::factory()->create())
        ->post(route('logout'))
        ->assertRedirect(route('home'));

    $this->assertGuest();
});

it('redirects guests away from the dashboard', function () {
    $this->get(route('dashboard'))->assertRedirect(route('login'));
});

it('redirects logged-in users away from the login page', function () {
    $this->actingAs(User::factory()->create())
        ->get(route('login'))
        ->assertRedirect(route('dashboard'));
});

it('lets people show and hide the password on every password field', function (string $route, int $fields) {
    $html = $this->get(route($route, $route === 'password.reset' ? ['token' => 'abc'] : []))->getContent();

    expect(substr_count($html, 'data-password-toggle'))->toBe($fields)
        ->and(substr_count($html, 'type="password"'))->toBe($fields)
        ->and($html)->toContain('aria-label="Show password"');
})->with([
    'login' => ['login', 1],
    'register' => ['register', 2],
    'reset password' => ['password.reset', 2],
]);

it('does not put a show/hide button on ordinary fields', function () {
    expect($this->get(route('login'))->getContent())->not->toMatch('/<input[^>]*name="email"[^>]*>\s*<button[^>]*data-password-toggle/');
});
