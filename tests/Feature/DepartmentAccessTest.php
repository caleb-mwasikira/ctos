<?php

use App\Enums\DepartmentRole;
use App\Models\Department;
use App\Models\Organization;
use App\Models\User;

beforeEach(function () {
    $this->organization = Organization::factory()->create(['slug' => 'acme']);
    $this->department = Department::factory()->for($this->organization)->create([
        'slug' => 'engineering',
        'access_code' => 'ABCDE23456',
    ]);
});

function joinDepartment(mixed $test, User $user, array $overrides = [])
{
    return $test->actingAs($user)->post(route('department-memberships.store'), [
        'code' => 'ABCDE23456',
        ...$overrides,
    ]);
}

it('joins a department with the correct access code', function () {
    $user = User::factory()->create();

    joinDepartment($this, $user)->assertRedirect(route('departments.show', $this->department));

    expect($this->department->members()->whereKey($user->id)->first()->pivot->role)->toBe(DepartmentRole::Member);
});

it('ignores case and surrounding whitespace in the access code', function () {
    $user = User::factory()->create();

    joinDepartment($this, $user, ['code' => '  abcde23456 '])->assertRedirect();

    expect($this->department->members()->whereKey($user->id)->exists())->toBeTrue();
});

it('rejects a wrong access code', function () {
    $user = User::factory()->create();

    joinDepartment($this, $user, ['code' => 'WRONGWRONG'])->assertSessionHasErrors('code');

    expect($this->department->members()->count())->toBe(0);
});

it('gives the same error for a code nobody has as for a wrong one', function () {
    $user = User::factory()->create();

    $message = 'That access code is incorrect.';

    joinDepartment($this, $user, ['code' => 'WRONGWRONG'])->assertSessionHasErrors(['code' => $message]);
    joinDepartment($this, $user, ['code' => 'NOSUCHCODE'])->assertSessionHasErrors(['code' => $message]);
});

it('does not duplicate or downgrade an existing membership', function () {
    $admin = User::factory()->create();
    $this->department->members()->attach($admin->id, ['role' => DepartmentRole::Admin->value]);

    joinDepartment($this, $admin)->assertRedirect();

    expect($this->department->members()->count())->toBe(1)
        ->and($this->department->members()->first()->pivot->role)->toBe(DepartmentRole::Admin);
});

it('only lets members open a department', function () {
    $member = User::factory()->create();
    $this->department->members()->attach($member->id, ['role' => DepartmentRole::Member->value]);

    $this->actingAs($member)->get(route('departments.show', $this->department))->assertOk();
    $this->actingAs(User::factory()->create())->get(route('departments.show', $this->department))->assertForbidden();
});

it('lets the organization owner or a department admin regenerate the code', function (string $who) {
    $user = $who === 'owner' ? $this->organization->owner : User::factory()->create();

    if ($who === 'admin') {
        $this->department->members()->attach($user->id, ['role' => DepartmentRole::Admin->value]);
    }

    $this->actingAs($user)
        ->post(route('departments.access-code.store', $this->department))
        ->assertSessionHas('access_code');

    $this->department->refresh();
    expect($this->department->acceptsAccessCode('ABCDE23456'))->toBeFalse()
        ->and($this->department->acceptsAccessCode(session('access_code')))->toBeTrue();
})->with(['owner', 'admin']);

it('keeps existing members when the code is regenerated', function () {
    $member = User::factory()->create();
    $this->department->members()->attach($member->id, ['role' => DepartmentRole::Member->value]);

    $this->actingAs($this->organization->owner)->post(route('departments.access-code.store', $this->department));

    expect($this->department->members()->whereKey($member->id)->exists())->toBeTrue();
});

it('stops regular members regenerating the code', function () {
    $member = User::factory()->create();
    $this->department->members()->attach($member->id, ['role' => DepartmentRole::Member->value]);

    $this->actingAs($member)
        ->post(route('departments.access-code.store', $this->department))
        ->assertForbidden();

    expect($this->department->fresh()->acceptsAccessCode('ABCDE23456'))->toBeTrue();
});

it('hides the regenerate button, and the access-code page itself, from regular members', function () {
    $member = User::factory()->create();
    $this->department->members()->attach($member->id, ['role' => DepartmentRole::Member->value]);

    $this->actingAs($member)
        ->get(route('departments.show', $this->department))
        ->assertDontSee('Generate new code');

    $this->actingAs($member)->get(route('departments.access-code.index', $this->department))->assertForbidden();
});

it('lets a member leave a department', function () {
    $member = User::factory()->create();
    $this->department->members()->attach($member->id, ['role' => DepartmentRole::Member->value]);

    $this->actingAs($member)
        ->delete(route('departments.membership.destroy', $this->department))
        ->assertRedirect(route('dashboard'));

    expect($this->department->members()->count())->toBe(0);
});

it('asks for confirmation on its own page before leaving', function () {
    $member = User::factory()->create();
    $this->department->members()->attach($member->id, ['role' => DepartmentRole::Member->value]);

    $this->actingAs($member)
        ->get(route('departments.membership.edit', $this->department))
        ->assertOk()
        ->assertSee('action="'.route('departments.membership.destroy', $this->department).'"', false);

    $this->actingAs(User::factory()->create())->get(route('departments.membership.edit', $this->department))->assertForbidden();
});

it('lists a user\'s departments on the departments page', function () {
    $member = User::factory()->create();
    $this->department->members()->attach($member->id, ['role' => DepartmentRole::Member->value]);

    $this->actingAs($member)->get(route('departments.index'))->assertOk()->assertSee($this->department->name);
});
