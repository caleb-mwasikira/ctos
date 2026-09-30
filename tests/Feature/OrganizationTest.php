<?php

use App\Enums\DepartmentRole;
use App\Models\Department;
use App\Models\Organization;
use App\Models\User;

// ── the organizations page ──────────────────────────────────────────────────

it('lists the organizations the user created and links to each one', function () {
    $organization = Organization::factory()->create(['name' => 'Acme Corp']);
    Department::factory()->count(2)->for($organization)->create();

    $this->actingAs($organization->owner)
        ->get(route('organizations.index'))
        ->assertOk()
        ->assertSee('Acme Corp')
        ->assertSee('Owner')
        ->assertSee('2 departments')
        ->assertSee(route('organizations.show', $organization), false);
});

it('lists an organization the user only joined a department of, as a member', function () {
    $organization = Organization::factory()->create(['name' => 'Someone Elses Company']);
    $joined = Department::factory()->for($organization)->create();
    Department::factory()->for($organization)->create();
    $member = User::factory()->create();
    $joined->members()->attach($member->id, ['role' => DepartmentRole::Member->value]);

    $this->actingAs($member)
        ->get(route('organizations.index'))
        ->assertSee('Someone Elses Company')
        ->assertSee('Member')
        ->assertSee('1 department joined');
});

it('does not list organizations the user has nothing to do with', function () {
    Organization::factory()->create(['name' => 'Unrelated Ltd']);

    $this->actingAs(User::factory()->create())->get(route('organizations.index'))->assertDontSee('Unrelated Ltd');
});

it('shows an empty state for a user with no organizations', function () {
    $this->actingAs(User::factory()->create())->get(route('organizations.index'))->assertSee('No organizations');
});

it('offers a create-organization action on the organizations page', function () {
    $this->actingAs(User::factory()->create())
        ->get(route('organizations.index'))
        ->assertSee('data-dialog-open="create-organization-dialog"', false)
        ->assertSee(route('organizations.store'), false);
});

it('reopens the create-organization dialog when the name is invalid', function () {
    $page = $this->actingAs(User::factory()->create())
        ->from(route('organizations.index'))
        ->followingRedirects()
        ->post(route('organizations.store'), ['name' => '']);

    expect($page->getContent())->toMatch('/<dialog\s+id="create-organization-dialog"[^>]*data-open/');
});

it('does not open the dialog on a normal visit', function () {
    $page = $this->actingAs(User::factory()->create())->get(route('organizations.index'));

    expect($page->getContent())->not->toContain('data-open');
});

// ── creating one ────────────────────────────────────────────────────────────

it('lets a user create an organization they own', function () {
    $user = User::factory()->create();

    $response = $this->actingAs($user)->post(route('organizations.store'), ['name' => 'Acme Corp']);

    $organization = Organization::firstWhere('name', 'Acme Corp');
    $response->assertRedirect(route('organizations.show', $organization));
    expect($organization->owner_id)->toBe($user->id)
        ->and($organization->slug)->toBe('acme-corp');
});

it('generates a unique slug when the name is already taken', function () {
    Organization::factory()->create(['name' => 'Acme Corp', 'slug' => 'acme-corp']);

    $this->actingAs(User::factory()->create())->post(route('organizations.store'), ['name' => 'Acme Corp']);

    expect(Organization::where('name', 'Acme Corp')->pluck('slug')->unique())->toHaveCount(2);
});

it('requires a name to create an organization', function () {
    $this->actingAs(User::factory()->create())
        ->post(route('organizations.store'), ['name' => ''])
        ->assertSessionHasErrors('name');
});

it('only shows an organization to its owner and to members of its departments', function () {
    $organization = Organization::factory()->create();
    $department = Department::factory()->for($organization)->create();
    $member = User::factory()->create();
    $department->members()->attach($member->id, ['role' => DepartmentRole::Member->value]);

    $this->actingAs($organization->owner)->get(route('organizations.show', $organization))->assertOk();
    $this->actingAs($member)->get(route('organizations.show', $organization))->assertOk();
    $this->actingAs(User::factory()->create())->get(route('organizations.show', $organization))->assertForbidden();
});

it('shows the owner every department and the form to add one', function () {
    $organization = Organization::factory()->create();
    Department::factory()->for($organization)->create(['name' => 'Engineering']);
    Department::factory()->for($organization)->create(['name' => 'Finance']);

    $this->actingAs($organization->owner)
        ->get(route('organizations.show', $organization))
        ->assertSee('Engineering')
        ->assertSee('Finance')
        ->assertSee('Add a department');
});

it('shows a member only the departments they joined and no way to add one', function () {
    $organization = Organization::factory()->create();
    $joined = Department::factory()->for($organization)->create(['name' => 'Engineering']);
    Department::factory()->for($organization)->create(['name' => 'Finance']);
    $member = User::factory()->create();
    $joined->members()->attach($member->id, ['role' => DepartmentRole::Member->value]);

    $this->actingAs($member)
        ->get(route('organizations.show', $organization))
        ->assertSee('Engineering')
        ->assertDontSee('Finance')
        ->assertDontSee('Add a department');
});

it('shows departments as cards with the user\'s role, and links only the ones they joined', function () {
    $organization = Organization::factory()->create();
    $joined = Department::factory()->for($organization)->create(['name' => 'Engineering']);
    $joined->members()->attach($organization->owner_id, ['role' => DepartmentRole::Admin->value]);
    $notJoined = Department::factory()->for($organization)->create(['name' => 'Finance']);

    $page = $this->actingAs($organization->owner)->get(route('organizations.show', $organization));

    $page->assertSee('Engineering')->assertSee('Admin')->assertSee('1 member')
        ->assertSee('href="'.route('departments.show', $joined).'"', false)
        ->assertSee('Finance')->assertSee('Not joined')
        ->assertDontSee('href="'.route('departments.show', $notJoined).'"', false);
});

it('adds a department from a popup that opens again if the name is invalid', function () {
    $organization = Organization::factory()->create();

    $page = $this->actingAs($organization->owner)->get(route('organizations.show', $organization));
    $page->assertSee('data-dialog-open="add-department-dialog"', false)
        ->assertSee(route('organizations.departments.store', $organization), false);
    expect($page->getContent())->not->toContain('data-open');

    $reopened = $this->actingAs($organization->owner)
        ->from(route('organizations.show', $organization))
        ->followingRedirects()
        ->post(route('organizations.departments.store', $organization), ['name' => '']);

    expect($reopened->getContent())->toMatch('/<dialog\s+id="add-department-dialog"[^>]*data-open/');
});

it('addresses organizations and departments by slug in URLs', function () {
    $organization = Organization::factory()->create(['name' => 'Acme Corp']);
    $department = Department::factory()->for($organization)->create(['name' => 'Engineering']);
    $department->members()->attach($organization->owner_id, ['role' => DepartmentRole::Admin->value]);

    expect(route('organizations.show', $organization, false))->toBe('/organizations/acme-corp')
        ->and(route('departments.show', $department, false))->toBe('/departments/engineering');

    $this->actingAs($organization->owner)->get('/organizations/acme-corp')->assertOk();
    $this->actingAs($organization->owner)->get('/departments/engineering')->assertOk();
    $this->actingAs($organization->owner)->get('/organizations/'.$organization->id)->assertNotFound();
    $this->actingAs($organization->owner)->get('/departments/'.$department->id)->assertNotFound();
});

it('gives every department an ID that is unique across all organizations', function () {
    $first = Organization::factory()->create();
    $second = Organization::factory()->create();

    $this->actingAs($first->owner)->post(route('organizations.departments.store', $first), ['name' => 'Engineering']);
    $this->actingAs($second->owner)->post(route('organizations.departments.store', $second), ['name' => 'Engineering']);

    $slugs = Department::where('name', 'Engineering')->pluck('slug');
    expect($slugs)->toHaveCount(2)->and($slugs->unique())->toHaveCount(2)
        ->and($slugs->contains('engineering'))->toBeTrue();
});

it('lets the owner create a department, joins them as admin and shows the code once', function () {
    $organization = Organization::factory()->create();

    $response = $this->actingAs($organization->owner)
        ->post(route('organizations.departments.store', $organization), ['name' => 'Engineering']);

    $department = $organization->departments()->firstWhere('name', 'Engineering');
    $response->assertRedirect(route('departments.show', $department))->assertSessionHas('access_code');

    $code = session('access_code');
    expect($code)->toHaveLength(10)
        ->and($department->acceptsAccessCode($code))->toBeTrue()
        ->and($department->members()->firstWhere('users.id', $organization->owner_id)->pivot->role)
        ->toBe(DepartmentRole::Admin);
});

it('never stores the access code in plain text', function () {
    $department = Department::factory()->create(['access_code' => 'ABCDE23456']);

    expect($department->getRawOriginal('access_code'))->not->toBe('ABCDE23456')
        ->and($department->acceptsAccessCode('ABCDE23456'))->toBeTrue();
});

it('stops non-owners creating departments', function () {
    $organization = Organization::factory()->create();

    $this->actingAs(User::factory()->create())
        ->post(route('organizations.departments.store', $organization), ['name' => 'Sneaky'])
        ->assertForbidden();

    expect($organization->departments()->count())->toBe(0);
});

// ── renaming ─────────────────────────────────────────────────────────────

it('lets the owner rename the organization without changing its slug', function () {
    $organization = Organization::factory()->create(['name' => 'Acme Corp', 'slug' => 'acme-corp']);

    $response = $this->actingAs($organization->owner)
        ->put(route('organizations.update', $organization), ['name' => 'Acme Corporation']);

    $response->assertRedirect()->assertSessionHas('status');
    expect($organization->fresh())
        ->name->toBe('Acme Corporation')
        ->slug->toBe('acme-corp');
});

it('requires a name to rename an organization', function () {
    $organization = Organization::factory()->create();

    $this->actingAs($organization->owner)
        ->put(route('organizations.update', $organization), ['name' => ''])
        ->assertSessionHasErrorsIn('renameOrganization', 'name');

    expect($organization->fresh()->name)->toBe($organization->name);
});

it('does not let anyone but the owner rename the organization', function () {
    $organization = Organization::factory()->create(['name' => 'Acme Corp']);
    $department = Department::factory()->for($organization)->create();
    $admin = User::factory()->create();
    $department->members()->attach($admin->id, ['role' => DepartmentRole::Admin->value]);

    $this->actingAs($admin)
        ->put(route('organizations.update', $organization), ['name' => 'Sneaky'])
        ->assertForbidden();
    $this->actingAs(User::factory()->create())
        ->put(route('organizations.update', $organization), ['name' => 'Sneaky'])
        ->assertForbidden();

    expect($organization->fresh()->name)->toBe('Acme Corp');
});
