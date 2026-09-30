<?php

use App\Enums\DepartmentRole;
use App\Models\Department;
use App\Models\Organization;
use App\Models\OrganizationLock;
use App\Models\User;
use App\Notifications\AccessLocked;
use App\Notifications\DepartmentLeadAssigned;
use Illuminate\Notifications\Events\NotificationSending;
use Illuminate\Support\Facades\Event;
use Illuminate\Support\Facades\Notification;

beforeEach(function () {
    $this->organization = Organization::factory()->create(['name' => 'Initech Holdings']);
    $this->owner = $this->organization->owner;
    $this->department = Department::factory()->for($this->organization)->create(['name' => 'Engineering', 'access_code' => 'ABCDE23456']);
    $this->department->members()->attach($this->owner->id, ['role' => DepartmentRole::Admin->value]);
});

function addMember(Department $department, string $role = 'member', array $attributes = []): User
{
    $user = User::factory()->create($attributes);
    $department->members()->attach($user->id, ['role' => $role]);

    return $user;
}

function pivotOf(Department $department, User $user)
{
    return $department->members()->whereKey($user->id)->first()->pivot;
}

// ── the members page ───────────────────────────────────────────────────────

it('shows the owner every member on a separate page', function () {
    addMember($this->department, 'member', ['name' => 'Bob Otieno', 'email' => 'bob@example.com']);
    addMember($this->department, 'lead', ['name' => 'Lena Lead']);

    $this->actingAs($this->owner)
        ->get(route('departments.members.index', $this->department))
        ->assertOk()
        ->assertSee('Bob Otieno')->assertSee('bob@example.com')
        ->assertSee('Lena Lead')->assertSee('Department lead')
        ->assertSee('Owner')
        ->assertSee('3 members');
});

it('links the members section of the department page to it for managers only', function () {
    $url = route('departments.members.index', $this->department);

    $this->actingAs($this->owner)->get(route('departments.show', $this->department))->assertSee('href="'.$url.'"', false);
    $this->actingAs(addMember($this->department))->get(route('departments.show', $this->department))->assertDontSee('href="'.$url.'"', false);
});

it('keeps the members page from regular members and outsiders', function () {
    $this->actingAs(addMember($this->department))->get(route('departments.members.index', $this->department))->assertForbidden();
    $this->actingAs(User::factory()->create())->get(route('departments.members.index', $this->department))->assertForbidden();
});

it('lets a lead view the members page but not change anything', function () {
    $lead = addMember($this->department, 'lead');
    addMember($this->department, 'member', ['name' => 'Bob Otieno']);

    $this->actingAs($lead)
        ->get(route('departments.members.index', $this->department))
        ->assertOk()
        ->assertSee('Bob Otieno')
        ->assertDontSee('Make department lead')
        ->assertSee('Only the owner');
});

it('offers the owner actions for members, but not for the owner themselves', function () {
    addMember($this->department);

    $this->actingAs($this->owner)
        ->get(route('departments.members.index', $this->department))
        ->assertSee('Make department lead')
        ->assertSee('Lock out of Engineering')
        ->assertSee('Lock out of Initech Holdings');
});

// ── department leads ───────────────────────────────────────────────────────

it('lets the owner make a member the department lead', function () {
    $member = addMember($this->department);

    $this->actingAs($this->owner)
        ->patch(route('departments.members.update', [$this->department, $member]), ['role' => 'lead'])
        ->assertSessionHas('status');

    expect(pivotOf($this->department, $member)->role)->toBe(DepartmentRole::Lead);
});

it('gives a lead the right to share the department and change its access code', function () {
    Notification::fake();
    $member = addMember($this->department);
    $this->actingAs($this->owner)->patch(route('departments.members.update', [$this->department, $member]), ['role' => 'lead']);

    $this->actingAs($member)->post(route('departments.invitations.store', $this->department), ['email' => 'new@example.com'])->assertSessionHas('status');
    $this->actingAs($member)->post(route('departments.access-code.store', $this->department))->assertSessionHas('access_code');
});

it('lets the owner take the lead role away again', function () {
    $lead = addMember($this->department, 'lead');

    $this->actingAs($this->owner)->patch(route('departments.members.update', [$this->department, $lead]), ['role' => 'member']);

    expect(pivotOf($this->department, $lead)->role)->toBe(DepartmentRole::Member);
    $this->actingAs($lead)->post(route('departments.access-code.store', $this->department))->assertForbidden();
});

it('only accepts the lead and member roles', function () {
    $member = addMember($this->department);

    $this->actingAs($this->owner)
        ->patch(route('departments.members.update', [$this->department, $member]), ['role' => 'admin'])
        ->assertSessionHasErrors('role');

    expect(pivotOf($this->department, $member)->role)->toBe(DepartmentRole::Member);
});

it('does not let leads, members or outsiders change roles or locks', function () {
    $lead = addMember($this->department, 'lead');
    $member = addMember($this->department);

    foreach ([$lead, $member, User::factory()->create()] as $actor) {
        $this->actingAs($actor)->patch(route('departments.members.update', [$this->department, $member]), ['role' => 'lead'])->assertForbidden();
        $this->actingAs($actor)->put(route('departments.members.lock.store', [$this->department, $member]))->assertForbidden();
        $this->actingAs($actor)->put(route('organizations.members.lock.store', [$this->organization, $member]))->assertForbidden();
    }

    expect(pivotOf($this->department, $member)->role)->toBe(DepartmentRole::Member)
        ->and(pivotOf($this->department, $member)->locked_at)->toBeNull()
        ->and(OrganizationLock::count())->toBe(0);
});

it('does not let the owner be changed or locked out', function () {
    $this->actingAs($this->owner)->patch(route('departments.members.update', [$this->department, $this->owner]), ['role' => 'member'])->assertForbidden();
    $this->actingAs($this->owner)->put(route('departments.members.lock.store', [$this->department, $this->owner]))->assertForbidden();
    $this->actingAs($this->owner)->put(route('organizations.members.lock.store', [$this->organization, $this->owner]))->assertForbidden();
});

it('cannot reach a member of another department through this one', function () {
    $other = Department::factory()->for($this->organization)->create();
    $stranger = addMember($other);

    $this->actingAs($this->owner)->patch(route('departments.members.update', [$this->department, $stranger]), ['role' => 'lead'])->assertNotFound();
    $this->actingAs($this->owner)->put(route('departments.members.lock.store', [$this->department, $stranger]))->assertNotFound();
});

// ── locking a member out of a department ───────────────────────────────────

it('locks a member out of a department and takes their access away', function () {
    $member = addMember($this->department);

    $this->actingAs($this->owner)->put(route('departments.members.lock.store', [$this->department, $member]))->assertSessionHas('status');

    expect(pivotOf($this->department, $member)->locked_at)->not->toBeNull();
    $this->actingAs($member)->get(route('departments.show', $this->department))->assertForbidden();
    $this->actingAs($member)->get(route('organizations.show', $this->organization))->assertForbidden();
    $this->actingAs($member)->get(route('organizations.index'))->assertDontSee('Initech Holdings');
    $this->actingAs($member)->get(route('departments.index'))->assertDontSee('Engineering');
});

it('restores access, and the role, when the member is unlocked', function () {
    $lead = addMember($this->department, 'lead');
    $this->actingAs($this->owner)->put(route('departments.members.lock.store', [$this->department, $lead]));

    $this->actingAs($this->owner)->delete(route('departments.members.lock.destroy', [$this->department, $lead]))->assertSessionHas('status');

    $this->actingAs($lead)->get(route('departments.show', $this->department))->assertOk();
    expect(pivotOf($this->department, $lead)->role)->toBe(DepartmentRole::Lead);
});

it('only locks the one department, not the organization\'s others', function () {
    $second = Department::factory()->for($this->organization)->create(['name' => 'Design']);
    $member = addMember($this->department);
    $second->members()->attach($member->id, ['role' => 'member']);

    $this->actingAs($this->owner)->put(route('departments.members.lock.store', [$this->department, $member]));

    $this->actingAs($member)->get(route('departments.show', $second))->assertOk();
    $this->actingAs($member)->get(route('organizations.show', $this->organization))->assertOk()->assertDontSee('Engineering')->assertSee('Design');
});

it('shows locked members on the members page, but hides them from the department page', function () {
    $member = addMember($this->department, 'member', ['name' => 'Bob Otieno']);
    $this->actingAs($this->owner)->put(route('departments.members.lock.store', [$this->department, $member]));

    $this->actingAs($this->owner)->get(route('departments.members.index', $this->department))
        ->assertSee('Bob Otieno')->assertSee('Locked in Engineering')->assertSee('Unlock in Engineering');
    $this->actingAs($this->owner)->get(route('departments.show', $this->department))->assertDontSee('Bob Otieno')->assertSee('1 member');
});

// ── locking a member out of the organization ───────────────────────────────

it('locks a member out of every department of the organization', function () {
    $second = Department::factory()->for($this->organization)->create();
    $member = addMember($this->department);
    $second->members()->attach($member->id, ['role' => 'member']);

    $this->actingAs($this->owner)->put(route('organizations.members.lock.store', [$this->organization, $member]))->assertSessionHas('status');

    $this->actingAs($member)->get(route('departments.show', $this->department))->assertForbidden();
    $this->actingAs($member)->get(route('departments.show', $second))->assertForbidden();
    $this->actingAs($member)->get(route('organizations.show', $this->organization))->assertForbidden();
    $this->actingAs($member)->get(route('organizations.index'))->assertDontSee('Initech Holdings');
    $this->actingAs($this->owner)->get(route('departments.members.index', $this->department))->assertSee('Locked out of Initech Holdings')->assertSee('Unlock in Initech Holdings');
});

it('gives the access back when the organization lock is removed', function () {
    $member = addMember($this->department);
    $this->actingAs($this->owner)->put(route('organizations.members.lock.store', [$this->organization, $member]));

    $this->actingAs($this->owner)->delete(route('organizations.members.lock.destroy', [$this->organization, $member]))->assertSessionHas('status');

    $this->actingAs($member)->get(route('departments.show', $this->department))->assertOk();
    expect(OrganizationLock::count())->toBe(0);
});

it('locks only within its own organization', function () {
    $otherOrganization = Organization::factory()->create();
    $otherDepartment = Department::factory()->for($otherOrganization)->create();
    $member = addMember($this->department);
    $otherDepartment->members()->attach($member->id, ['role' => 'member']);

    $this->actingAs($this->owner)->put(route('organizations.members.lock.store', [$this->organization, $member]));

    $this->actingAs($member)->get(route('departments.show', $otherDepartment))->assertOk();
});

it('only locks people who belong to the organization', function () {
    $this->actingAs($this->owner)->put(route('organizations.members.lock.store', [$this->organization, User::factory()->create()]))->assertNotFound();
});

it('does not create the same organization lock twice', function () {
    $member = addMember($this->department);

    $this->actingAs($this->owner)->put(route('organizations.members.lock.store', [$this->organization, $member]));
    $this->actingAs($this->owner)->put(route('organizations.members.lock.store', [$this->organization, $member]));

    expect(OrganizationLock::count())->toBe(1);
});

// ── locked people cannot get back in ───────────────────────────────────────

it('does not let a locked member get back in with the access code', function (string $kind) {
    $member = addMember($this->department);
    $route = $kind === 'department'
        ? route('departments.members.lock.store', [$this->department, $member])
        : route('organizations.members.lock.store', [$this->organization, $member]);
    $this->actingAs($this->owner)->put($route);

    $this->actingAs($member)->post(route('department-memberships.store'), ['code' => 'ABCDE23456'])
        ->assertSessionHasErrors('code');

    $this->actingAs($member)->get(route('departments.show', $this->department))->assertForbidden();
})->with(['department', 'organization']);

it('does not share a department with someone who is locked out of it', function () {
    $member = addMember($this->department);
    $this->actingAs($this->owner)->put(route('departments.members.lock.store', [$this->department, $member]));
    Notification::fake();

    $this->actingAs($this->owner)
        ->post(route('departments.invitations.store', $this->department), ['email' => $member->email])
        ->assertSessionHasErrors('email');

    Notification::assertNothingSent();
});

it('does not count locked members on the organization page', function () {
    $member = addMember($this->department);
    $this->actingAs($this->owner)->put(route('departments.members.lock.store', [$this->department, $member]));

    $this->actingAs($this->owner)->get(route('organizations.show', $this->organization))->assertSee('1 member')->assertDontSee('2 members');
});

// ── the access code page ─────────────────────────────────────────────────────

it('generates the access code from a popup on its own page, not an inline button', function () {
    $html = $this->actingAs($this->owner)->get(route('departments.access-code.index', $this->department))->getContent();

    expect($html)->toContain('data-dialog-open="generate-code-dialog"')
        ->and($html)->toMatch('/<dialog\s+id="generate-code-dialog"/')
        ->and($html)->toContain('action="'.route('departments.access-code.store', $this->department).'"');
});

it('links to the access code page from the department page, instead of embedding it', function () {
    $this->actingAs($this->owner)
        ->get(route('departments.show', $this->department))
        ->assertDontSee('data-dialog-open="generate-code-dialog"', false)
        ->assertSee('href="'.route('departments.access-code.index', $this->department).'"', false);
});

// ── emails ─────────────────────────────────────────────────────────────────

it('emails a member who is made a department lead', function () {
    Notification::fake();
    $member = addMember($this->department);

    $this->actingAs($this->owner)
        ->patch(route('departments.members.update', [$this->department, $member]), ['role' => 'lead'])
        ->assertSessionHas('status', fn (string $status) => str_contains($status, 'They have been emailed'));

    Notification::assertSentTo($member, DepartmentLeadAssigned::class, fn ($notification) => $notification->department->is($this->department));
});

it('does not email again when a lead is made a lead a second time, or when the lead role is taken away', function () {
    $lead = addMember($this->department, 'lead');
    Notification::fake();

    $this->actingAs($this->owner)->patch(route('departments.members.update', [$this->department, $lead]), ['role' => 'lead']);
    $this->actingAs($this->owner)->patch(route('departments.members.update', [$this->department, $lead]), ['role' => 'member']);

    Notification::assertNothingSent();
});

it('emails a member who is locked out of a department, once', function () {
    Notification::fake();
    $member = addMember($this->department);

    $this->actingAs($this->owner)->put(route('departments.members.lock.store', [$this->department, $member]))
        ->assertSessionHas('status', fn (string $status) => str_contains($status, 'They have been emailed'));
    $this->actingAs($this->owner)->put(route('departments.members.lock.store', [$this->department, $member]));

    Notification::assertSentToTimes($member, AccessLocked::class, 1);
    Notification::assertSentTo($member, AccessLocked::class, fn ($notification) => $notification->department?->is($this->department));
});

it('emails a member who is locked out of the whole organization, once', function () {
    Notification::fake();
    $member = addMember($this->department);

    $this->actingAs($this->owner)->put(route('organizations.members.lock.store', [$this->organization, $member]));
    $this->actingAs($this->owner)->put(route('organizations.members.lock.store', [$this->organization, $member]));

    Notification::assertSentToTimes($member, AccessLocked::class, 1);
    Notification::assertSentTo($member, AccessLocked::class, fn ($notification) => $notification->department === null);
});

it('does not email when a member is unlocked', function () {
    $member = addMember($this->department);
    $this->actingAs($this->owner)->put(route('departments.members.lock.store', [$this->department, $member]));
    $this->actingAs($this->owner)->put(route('organizations.members.lock.store', [$this->organization, $member]));
    Notification::fake();

    $this->actingAs($this->owner)->delete(route('departments.members.lock.destroy', [$this->department, $member]));
    $this->actingAs($this->owner)->delete(route('organizations.members.lock.destroy', [$this->organization, $member]));

    Notification::assertNothingSent();
});

it('still applies the lock and the lead role when the email cannot be sent', function () {
    Event::listen(NotificationSending::class, fn () => throw new RuntimeException('SMTP is down'));
    $locked = addMember($this->department);
    $promoted = addMember($this->department);

    $this->actingAs($this->owner)->put(route('departments.members.lock.store', [$this->department, $locked]))
        ->assertSessionHas('status', fn (string $status) => str_contains($status, 'We could not email them'));
    $this->actingAs($this->owner)->put(route('organizations.members.lock.store', [$this->organization, $promoted]))->assertSessionHas('status');
    $this->actingAs($this->owner)->delete(route('organizations.members.lock.destroy', [$this->organization, $promoted]));
    $this->actingAs($this->owner)->patch(route('departments.members.update', [$this->department, $promoted]), ['role' => 'lead'])
        ->assertSessionHas('status', fn (string $status) => str_contains($status, 'We could not email them'));

    expect(pivotOf($this->department, $locked)->locked_at)->not->toBeNull()
        ->and(pivotOf($this->department, $promoted)->role)->toBe(DepartmentRole::Lead);
});

it('words the lead email for the person and links to the department', function () {
    $member = addMember($this->department, 'member', ['name' => 'Bob Otieno']);

    $mail = (new DepartmentLeadAssigned($this->department, $this->owner))->toMail($member);

    expect($mail->subject)->toBe('You are now a lead of Engineering')
        ->and($mail->greeting)->toBe('Hello Bob Otieno!')
        ->and($mail->actionUrl)->toBe(route('departments.show', $this->department))
        ->and(implode(' ', $mail->introLines))->toContain($this->owner->name)->toContain('Initech Holdings');
});

it('words the lock email for a department and for the whole organization, without a link', function () {
    $member = addMember($this->department, 'member', ['name' => 'Bob Otieno']);

    $department = (new AccessLocked($this->organization, $this->department, $this->owner))->toMail($member);
    $organization = (new AccessLocked($this->organization, null, $this->owner))->toMail($member);

    expect($department->subject)->toBe('Your access to Engineering was locked')
        ->and(implode(' ', $department->introLines))->toContain('the Engineering department of Initech Holdings')->toContain('Your other departments are not affected')
        ->and($organization->subject)->toBe('Your access to Initech Holdings was locked')
        ->and(implode(' ', $organization->introLines))->toContain('any department of this organization')
        ->and($department->actionUrl)->toBeNull()->and($organization->actionUrl)->toBeNull();
});

// ── dropdown menus ─────────────────────────────────────────────────────────

it('marks the dropdown menus so they close when you click elsewhere', function () {
    addMember($this->department);

    $members = $this->actingAs($this->owner)->get(route('departments.members.index', $this->department))->getContent();
    $dashboard = $this->actingAs($this->owner)->get(route('dashboard'))->getContent();

    expect($members)->toContain('<details class="relative" data-menu>')
        ->and(substr_count($dashboard, 'data-menu'))->toBe(1);
});
