<?php

use App\Enums\DepartmentRole;
use App\Models\Department;
use App\Models\Organization;
use App\Models\User;

beforeEach(function () {
    $this->organization = Organization::factory()->create();
    $this->department = Department::factory()->for($this->organization)->create(['name' => 'Engineering', 'slug' => 'engineering']);
});

function memberWithRole(Department $department, string $role): User
{
    $user = User::factory()->create();
    $department->members()->attach($user->id, ['role' => $role]);

    return $user;
}

it('lets an admin or a lead rename the department without changing its slug', function (string $role) {
    $user = memberWithRole($this->department, $role);

    $response = $this->actingAs($user)->put(route('departments.update', $this->department), ['name' => 'Platform Engineering']);

    $response->assertRedirect()->assertSessionHas('status');
    expect($this->department->fresh())
        ->name->toBe('Platform Engineering')
        ->slug->toBe('engineering');
})->with([
    'admin' => [DepartmentRole::Admin->value],
    'lead' => [DepartmentRole::Lead->value],
]);

it('lets the organization owner rename the department even without a membership', function () {
    $response = $this->actingAs($this->organization->owner)
        ->put(route('departments.update', $this->department), ['name' => 'Platform Engineering']);

    $response->assertRedirect()->assertSessionHas('status');
    expect($this->department->fresh()->name)->toBe('Platform Engineering');
});

it('does not let a plain member rename the department', function () {
    $member = memberWithRole($this->department, DepartmentRole::Member->value);

    $this->actingAs($member)
        ->put(route('departments.update', $this->department), ['name' => 'Sneaky'])
        ->assertForbidden();

    expect($this->department->fresh()->name)->toBe('Engineering');
});

it('does not let an outsider rename the department', function () {
    $this->actingAs(User::factory()->create())
        ->put(route('departments.update', $this->department), ['name' => 'Sneaky'])
        ->assertForbidden();

    expect($this->department->fresh()->name)->toBe('Engineering');
});

it('requires a name to rename the department', function () {
    $admin = memberWithRole($this->department, DepartmentRole::Admin->value);

    $this->actingAs($admin)
        ->put(route('departments.update', $this->department), ['name' => ''])
        ->assertSessionHasErrorsIn('renameDepartment', 'name');

    expect($this->department->fresh()->name)->toBe('Engineering');
});
