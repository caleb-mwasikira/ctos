<?php

use App\Enums\DepartmentRole;
use App\Models\Department;
use App\Models\Organization;
use App\Models\User;

/**
 * Create a department in a new organization and add the user to it.
 */
function joinedDepartment(User $user, string $name, ?Organization $organization = null): Department
{
    $department = Department::factory()->for($organization ?? Organization::factory()->create())->create(['name' => $name]);
    $department->members()->attach($user->id, ['role' => DepartmentRole::Member->value]);

    return $department;
}

it('lists the departments a user joined across every organization', function () {
    $user = User::factory()->create();
    joinedDepartment($user, 'Engineering', Organization::factory()->create(['name' => 'Acme Corp']));
    joinedDepartment($user, 'Design', Organization::factory()->create(['name' => 'Globex']));

    $this->actingAs($user)
        ->get(route('departments.index'))
        ->assertOk()
        ->assertSee('Engineering')->assertSee('Acme Corp')
        ->assertSee('Design')->assertSee('Globex');
});

it('never lists departments the user has not joined', function () {
    $user = User::factory()->create();
    joinedDepartment($user, 'Engineering');
    Department::factory()->create(['name' => 'Secret Finance']);

    $this->actingAs($user)->get(route('departments.index'))->assertDontSee('Secret Finance');
    $this->actingAs($user)->get(route('departments.index', ['search' => 'Secret']))->assertDontSee('Secret Finance');
});

it('searches department names across organizations, ignoring case', function () {
    $user = User::factory()->create();
    joinedDepartment($user, 'Platform Engineering');
    joinedDepartment($user, 'Data ENGINEERING');
    joinedDepartment($user, 'Finance');

    $this->actingAs($user)
        ->get(route('departments.index', ['search' => 'engineering']))
        ->assertSee('Platform Engineering')
        ->assertSee('Data ENGINEERING')
        ->assertDontSee('Finance')
        ->assertSee('2 departments');
});

it('matches the search text literally, not as a wildcard', function () {
    $user = User::factory()->create();
    joinedDepartment($user, '100% Remote');
    joinedDepartment($user, 'Sales');

    $this->actingAs($user)->get(route('departments.index', ['search' => '%']))
        ->assertSee('100% Remote')
        ->assertDontSee('Sales');

    $this->actingAs($user)->get(route('departments.index', ['search' => '_']))
        ->assertDontSee('100% Remote')
        ->assertDontSee('Sales');
});

it('says when nothing matches the search and offers to clear it', function () {
    $user = User::factory()->create();
    joinedDepartment($user, 'Engineering');

    $this->actingAs($user)
        ->get(route('departments.index', ['search' => 'zzz']))
        ->assertSee('No departments match')
        ->assertSee('Clear search')
        ->assertDontSee('Engineering');
});

it('shows an empty state to a user who has joined nothing', function () {
    $this->actingAs(User::factory()->create())
        ->get(route('departments.index'))
        ->assertSee('No departments');
});

it('paginates the results and keeps the search in the page links', function () {
    $user = User::factory()->create();
    foreach (range(1, 25) as $number) {
        joinedDepartment($user, sprintf('Team %02d', $number));
    }

    $this->actingAs($user)->get(route('departments.index', ['search' => 'Team']))
        ->assertSee('25 departments')
        ->assertSee('Team 01')
        ->assertDontSee('Team 25')
        ->assertSee('search=Team', false);

    $this->actingAs($user)->get(route('departments.index', ['search' => 'Team', 'page' => 2]))
        ->assertSee('Team 25')
        ->assertDontSee('Team 01');
});

it('requires a login and a verified email', function () {
    $this->get(route('departments.index'))->assertRedirect(route('login'));

    $this->actingAs(User::factory()->unverified()->create())
        ->get(route('departments.index'))
        ->assertRedirect(route('verification.notice'));
});

// ── joining a department from this page ─────────────────────────────────────

it('offers a join-department action on the departments page', function () {
    $this->actingAs(User::factory()->create())
        ->get(route('departments.index'))
        ->assertSee('data-dialog-open="join-department-dialog"', false)
        ->assertSee(route('department-memberships.store'), false);
});

it('reopens the join-department dialog when the access code is wrong', function () {
    $page = $this->actingAs(User::factory()->create())
        ->from(route('departments.index'))
        ->followingRedirects()
        ->post(route('department-memberships.store'), ['department' => 'nope', 'code' => 'WRONG']);

    expect($page->getContent())->toMatch('/<dialog\s+id="join-department-dialog"[^>]*data-open/');
});

it('does not open the dialog on a normal visit', function () {
    $page = $this->actingAs(User::factory()->create())->get(route('departments.index'));

    expect($page->getContent())->not->toContain('data-open');
});
