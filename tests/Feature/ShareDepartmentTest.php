<?php

use App\Enums\DepartmentRole;
use App\Models\Department;
use App\Models\DepartmentInvitation;
use App\Models\Organization;
use App\Models\User;
use App\Notifications\DepartmentShared;
use Illuminate\Auth\Events\Verified;
use Illuminate\Notifications\Events\NotificationSending;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Event;
use Illuminate\Support\Facades\Notification;
use Illuminate\Support\Facades\Password;
use Illuminate\Support\Facades\URL;

beforeEach(function () {
    $this->organization = Organization::factory()->create(['name' => 'Acme Corp']);
    $this->owner = $this->organization->owner;
    $this->department = Department::factory()->for($this->organization)->create(['name' => 'Engineering']);
    $this->department->members()->attach($this->owner->id, ['role' => DepartmentRole::Admin->value]);
    $this->code = $this->department->regenerateAccessCode();
});

function share(mixed $test, User $as, string $email)
{
    return $test->actingAs($as)->post(route('departments.invitations.store', $test->department), ['email' => $email]);
}

function memberOf(Department $department, string $role = 'member'): User
{
    $user = User::factory()->create();
    $department->members()->attach($user->id, ['role' => $role]);

    return $user;
}

// ── copying the access code ────────────────────────────────────────────────

it('shows admins the access code with a copy button', function () {
    $this->actingAs($this->owner)
        ->get(route('departments.access-code.index', $this->department))
        ->assertOk()
        ->assertSee('data-copy="'.$this->code.'"', false)
        ->assertSee($this->code);
});

it('shows the new code after it is regenerated', function () {
    $this->actingAs($this->owner)->post(route('departments.access-code.store', $this->department));
    $newCode = $this->department->fresh()->access_code_encrypted;

    expect($newCode)->not->toBe($this->code);
    $this->actingAs($this->owner)->get(route('departments.access-code.index', $this->department))->assertSee('data-copy="'.$newCode.'"', false);
});

it('keeps the copy of the access code encrypted, next to the hash used for joining', function () {
    $raw = $this->department->getRawOriginal('access_code_encrypted');

    expect($raw)->not->toContain($this->code)
        ->and($this->department->fresh()->access_code_encrypted)->toBe($this->code)
        ->and($this->department->fresh()->acceptsAccessCode($this->code))->toBeTrue();
});

it('hides the code, the share button and invitations from regular members', function () {
    DepartmentInvitation::factory()->for($this->department)->create(['email' => 'pending@example.com']);
    $member = memberOf($this->department);

    $this->actingAs($member)
        ->get(route('departments.show', $this->department))
        ->assertOk()
        ->assertDontSee($this->code)
        ->assertDontSee('pending@example.com');

    $this->actingAs($member)->get(route('departments.access-code.index', $this->department))->assertForbidden();
    $this->actingAs($member)->get(route('departments.invitations.index', $this->department))->assertForbidden();
});

it('asks for a new code when the department has no copyable one yet', function () {
    $legacy = Department::factory()->for($this->organization)->create();
    $legacy->members()->attach($this->owner->id, ['role' => DepartmentRole::Admin->value]);

    $this->actingAs($this->owner)
        ->get(route('departments.access-code.index', $legacy))
        ->assertSee('Generate a new one to see it here');
});

it('offers a new code instead of failing when the stored copy cannot be decrypted', function () {
    $this->department->forceFill(['access_code_encrypted' => null])->save();
    DB::table('departments')->where('id', $this->department->id)->update(['access_code_encrypted' => 'not-a-valid-payload']);

    $this->actingAs($this->owner)
        ->get(route('departments.access-code.index', $this->department))
        ->assertOk()
        ->assertSee('Generate a new one to see it here');
});

// ── sharing by email ───────────────────────────────────────────────────────

it('shares with an email that has no account by sending an invitation', function () {
    Notification::fake();

    share($this, $this->owner, 'new@example.com')->assertSessionHas('status')->assertSessionHasNoErrors();

    expect(DepartmentInvitation::where('department_id', $this->department->id)->pluck('email')->all())->toBe(['new@example.com']);
    Notification::assertSentOnDemand(
        DepartmentShared::class,
        fn (DepartmentShared $notification, array $channels, object $notifiable) => $notifiable->routes['mail'] === 'new@example.com'
            && $notification->hasAccess === false
    );
});

it('gives an existing verified user access straight away', function () {
    Notification::fake();
    $user = User::factory()->create(['email' => 'known@example.com']);

    share($this, $this->owner, 'known@example.com')->assertSessionHas('status');

    expect($this->department->members()->whereKey($user->id)->first()->pivot->role)->toBe(DepartmentRole::Member)
        ->and(DepartmentInvitation::count())->toBe(0);
    Notification::assertSentOnDemand(DepartmentShared::class, fn (DepartmentShared $notification) => $notification->hasAccess === true);
});

it('only invites an existing account whose email is not verified yet', function () {
    Notification::fake();
    $user = User::factory()->unverified()->create(['email' => 'unverified@example.com']);

    share($this, $this->owner, 'unverified@example.com')->assertSessionHas('status');

    expect($this->department->members()->whereKey($user->id)->exists())->toBeFalse()
        ->and(DepartmentInvitation::where('email', 'unverified@example.com')->exists())->toBeTrue();
});

it('ignores the case of the email and does not create duplicate invitations', function () {
    Notification::fake();

    share($this, $this->owner, 'New@Example.COM');
    share($this, $this->owner, 'new@example.com');

    expect(DepartmentInvitation::pluck('email')->all())->toBe(['new@example.com']);
    Notification::assertSentOnDemandTimes(DepartmentShared::class, 2);
});

it('refuses to share with someone who already has access', function () {
    Notification::fake();
    $member = memberOf($this->department);

    share($this, $this->owner, $member->email)->assertSessionHasErrors('email');

    Notification::assertNothingSent();
});

it('rejects an invalid email and reopens the share popup', function () {
    $page = $this->actingAs($this->owner)
        ->from(route('departments.invitations.index', $this->department))
        ->followingRedirects()
        ->post(route('departments.invitations.store', $this->department), ['email' => 'not-an-email']);

    expect($page->getContent())->toMatch('/<dialog\s+id="share-department-dialog"[^>]*data-open/');
});

it('grants nothing when the email cannot be sent', function () {
    Event::listen(NotificationSending::class, fn () => throw new RuntimeException('SMTP is down'));
    $user = User::factory()->create(['email' => 'known@example.com']);

    share($this, $this->owner, 'known@example.com')->assertSessionHasErrors('email');
    share($this, $this->owner, 'new@example.com')->assertSessionHasErrors('email');

    expect($this->department->members()->whereKey($user->id)->exists())->toBeFalse()
        ->and(DepartmentInvitation::count())->toBe(0);
});

it('lets department admins share, but not regular members or outsiders', function () {
    Notification::fake();

    share($this, memberOf($this->department, 'admin'), 'a@example.com')->assertSessionHas('status');
    share($this, memberOf($this->department), 'b@example.com')->assertForbidden();
    share($this, User::factory()->create(), 'c@example.com')->assertForbidden();

    expect(DepartmentInvitation::pluck('email')->all())->toBe(['a@example.com']);
});

it('requires a verified login to share', function () {
    $this->post(route('departments.invitations.store', $this->department), ['email' => 'a@example.com'])
        ->assertRedirect(route('login'));

    share($this, User::factory()->unverified()->create(), 'a@example.com')->assertRedirect(route('verification.notice'));
});

it('emails a link to sign up, without putting the address in the link itself', function () {
    $mail = (new DepartmentShared($this->department, $this->owner, 'new@example.com', false))->toMail((object) []);

    expect($mail->actionUrl)->toBe(route('register'))
        ->and($mail->actionUrl)->not->toContain('new@example.com')
        ->and(implode(' ', $mail->outroLines))->toContain('new@example.com')
        ->and($mail->subject)->toContain('Engineering');
});

it('emails a link to the department when the person already has access', function () {
    $mail = (new DepartmentShared($this->department, $this->owner, 'known@example.com', true))->toMail((object) []);

    expect($mail->actionUrl)->toBe(route('departments.show', $this->department));
});

it('prefills the invited email on the sign-up page', function () {
    $this->get(route('register', ['email' => 'new@example.com']))->assertSee('value="new@example.com"', false);
});

// ── withdrawing an invitation ──────────────────────────────────────────────

it('lists pending invitations to admins and lets them withdraw one', function () {
    $invitation = DepartmentInvitation::factory()->for($this->department)->create(['email' => 'pending@example.com']);

    $this->actingAs($this->owner)->get(route('departments.invitations.index', $this->department))->assertSee('pending@example.com');

    $this->actingAs($this->owner)
        ->delete(route('departments.invitations.destroy', [$this->department, $invitation]))
        ->assertSessionHas('status');

    expect(DepartmentInvitation::count())->toBe(0);
});

it('does not let members withdraw invitations', function () {
    $invitation = DepartmentInvitation::factory()->for($this->department)->create();

    $this->actingAs(memberOf($this->department))
        ->delete(route('departments.invitations.destroy', [$this->department, $invitation]))
        ->assertForbidden();

    expect(DepartmentInvitation::count())->toBe(1);
});

it('does not withdraw an invitation that belongs to another department', function () {
    $other = Department::factory()->for($this->organization)->create();
    $invitation = DepartmentInvitation::factory()->for($other)->create();

    $this->actingAs($this->owner)
        ->delete(route('departments.invitations.destroy', [$this->department, $invitation]))
        ->assertNotFound();

    expect(DepartmentInvitation::count())->toBe(1);
});

// ── accepting an invitation ────────────────────────────────────────────────

it('gives access when the invited address is verified', function () {
    DepartmentInvitation::factory()->for($this->department)->create(['email' => 'new@example.com']);
    $user = User::factory()->unverified()->create(['email' => 'new@example.com']);

    $link = URL::temporarySignedRoute('verification.verify', now()->addMinutes(60), ['id' => $user->id, 'hash' => sha1($user->email)]);
    $this->actingAs($user)->get($link)->assertRedirect(route('dashboard'));

    expect($this->department->members()->whereKey($user->id)->first()->pivot->role)->toBe(DepartmentRole::Member)
        ->and(DepartmentInvitation::count())->toBe(0);
});

it('gives access when the address is confirmed by resetting the password', function () {
    DepartmentInvitation::factory()->for($this->department)->create(['email' => 'new@example.com']);
    $user = User::factory()->unverified()->create(['email' => 'new@example.com']);

    $this->post(route('password.update'), [
        'token' => Password::createToken($user),
        'email' => $user->email,
        'password' => 'brand-new-password',
        'password_confirmation' => 'brand-new-password',
    ])->assertRedirect(route('login'));

    expect($this->department->members()->whereKey($user->id)->exists())->toBeTrue();
});

it('does not give access to someone with a different address', function () {
    DepartmentInvitation::factory()->for($this->department)->create(['email' => 'new@example.com']);
    $stranger = User::factory()->unverified()->create(['email' => 'stranger@example.com']);

    event(new Verified($stranger));

    expect($this->department->members()->whereKey($stranger->id)->exists())->toBeFalse()
        ->and(DepartmentInvitation::count())->toBe(1);
});

it('does not give access before the address is verified', function () {
    Notification::fake();
    share($this, $this->owner, 'new@example.com');

    $user = User::factory()->unverified()->create(['email' => 'new@example.com']);

    expect($this->department->members()->whereKey($user->id)->exists())->toBeFalse();
});

it('accepts several invitations at once and never downgrades an existing role', function () {
    $second = Department::factory()->for($this->organization)->create();
    DepartmentInvitation::factory()->for($this->department)->create(['email' => 'new@example.com']);
    DepartmentInvitation::factory()->for($second)->create(['email' => 'new@example.com']);
    $user = User::factory()->unverified()->create(['email' => 'new@example.com']);
    $second->members()->attach($user->id, ['role' => DepartmentRole::Admin->value]);

    event(new Verified($user));

    expect($this->department->members()->whereKey($user->id)->exists())->toBeTrue()
        ->and($second->members()->whereKey($user->id)->first()->pivot->role)->toBe(DepartmentRole::Admin)
        ->and(DepartmentInvitation::count())->toBe(0);
});
