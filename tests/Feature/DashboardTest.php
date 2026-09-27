<?php

use App\Enums\DepartmentRole;
use App\Models\Department;
use App\Models\Organization;
use App\Models\User;

it('shows a shortcut to organizations and to departments, with how many of each', function () {
    $organization = Organization::factory()->create();
    Department::factory()->for($organization)->create();
    $department = Department::factory()->for($organization)->create();
    $department->members()->attach($organization->owner_id, ['role' => DepartmentRole::Admin->value]);

    $this->actingAs($organization->owner)
        ->get(route('dashboard'))
        ->assertOk()
        ->assertSee('1 organization')
        ->assertSee('1 department joined')
        ->assertSee('href="'.route('organizations.index').'"', false)
        ->assertSee('href="'.route('departments.index').'"', false);
});

it('never shows an organization or department by name on the dashboard', function () {
    $organization = Organization::factory()->create(['name' => 'Initech Holdings']);
    $department = Department::factory()->for($organization)->create(['name' => 'Engineering']);
    $department->members()->attach($organization->owner_id, ['role' => DepartmentRole::Admin->value]);

    $this->actingAs($organization->owner)
        ->get(route('dashboard'))
        ->assertDontSee('Initech Holdings')
        ->assertDontSee('Engineering');
});

it('does not put either the create-organization or the join-department action on the dashboard', function () {
    $this->actingAs(User::factory()->create())
        ->get(route('dashboard'))
        ->assertDontSee('data-dialog-open="create-organization-dialog"', false)
        ->assertDontSee('data-dialog-open="join-department-dialog"', false);
});

it('has a search icon in the navbar that opens a search popup', function () {
    $html = $this->actingAs(User::factory()->create())->get(route('dashboard'))->getContent();
    preg_match('/<header.*?<\/header>/s', $html, $header);

    expect($header[0])->toContain('data-dialog-open="search-dialog"')
        ->and($header[0])->not->toContain('name="search"')
        ->and($html)->toMatch('/<dialog\s+id="search-dialog"/')
        ->and($html)->toContain('role="search"')
        ->and($html)->toContain('name="search"');
});

it('submits the search popup to the departments page', function () {
    $this->actingAs(User::factory()->create())
        ->get(route('dashboard'))
        ->assertSee('action="'.route('departments.index').'"', false);
});

it('keeps the current search text in the popup on the departments page', function () {
    $this->actingAs(User::factory()->create())
        ->get(route('departments.index', ['search' => 'payroll']))
        ->assertSee('value="payroll"', false);
});

it('does not put a search form in the page content of the departments page', function () {
    $html = $this->actingAs(User::factory()->create())->get(route('departments.index'))->getContent();
    preg_match('/<main.*?<\/main>/s', $html, $main);

    expect($main[0])->not->toContain('role="search"')->and($main[0])->not->toContain('name="search"');
});

// ── the sidebar / mobile drawer ─────────────────────────────────────────────

it('links every section from the sidebar and the mobile drawer, and marks the current one', function () {
    // The departments page itself links to none of these, so every match comes from navigation.
    $html = $this->actingAs(User::factory()->create())->get(route('departments.index'))->getContent();

    preg_match('/<aside.*?<\/aside>/s', $html, $sidebar);
    preg_match('/<dialog\s+id="mobile-menu-dialog".*?<\/dialog>/s', $html, $drawer);

    foreach ([$sidebar[0], $drawer[0]] as $nav) {
        expect($nav)->toContain('href="'.route('dashboard').'"')
            ->toContain('href="'.route('organizations.index').'"')
            ->toContain('href="'.route('departments.index').'"')
            ->toContain('href="'.route('downloads').'"')
            ->toContain('aria-current="page"'); // "Departments" is the current section.
    }
});

it('has a mobile menu button that opens a drawer with the same sections and a log out', function () {
    $html = $this->actingAs(User::factory()->create())->get(route('dashboard'))->getContent();

    expect($html)->toContain('data-dialog-open="mobile-menu-dialog"')
        ->and($html)->toMatch('/<dialog\s+id="mobile-menu-dialog"/')
        ->and($html)->toContain('action="'.route('logout').'"');
});
