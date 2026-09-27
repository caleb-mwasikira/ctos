<?php

namespace App\Actions;

use App\Enums\DepartmentRole;
use App\Models\Department;
use App\Models\User;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\ValidationException;

class JoinDepartmentAction
{
    /**
     * Add the user to a department when the access code matches.
     *
     * Every failure returns the same message so callers cannot probe which
     * departments exist.
     *
     * @throws ValidationException
     */
    public function handle(User $user, string $departmentSlug, string $code): Department
    {
        $department = Department::where('slug', $departmentSlug)->first();

        if ($department === null) {
            Hash::make($code);

            throw $this->invalidCredentials();
        }

        if (! $department->acceptsAccessCode($code)) {
            throw $this->invalidCredentials();
        }

        if ($department->isLockedFor($user)) {
            throw ValidationException::withMessages([
                'code' => 'Your access to this department has been locked by the organization owner.',
            ]);
        }

        if (! $department->members()->whereKey($user->id)->exists()) {
            $department->members()->attach($user->id, ['role' => DepartmentRole::Member->value]);
        }

        return $department;
    }

    private function invalidCredentials(): ValidationException
    {
        return ValidationException::withMessages([
            'code' => 'The department ID or access code is incorrect.',
        ]);
    }
}
