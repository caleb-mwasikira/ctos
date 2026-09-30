<?php

namespace App\Actions;

use App\Enums\DepartmentRole;
use App\Models\Department;
use App\Models\User;
use Illuminate\Validation\ValidationException;

class JoinDepartmentAction
{
    /**
     * Add the user to the department whose access code this is.
     *
     * Every failure returns the same message so callers cannot probe which codes are close.
     *
     * @throws ValidationException
     */
    public function handle(User $user, string $code): Department
    {
        $department = Department::findByAccessCode($code);

        if ($department === null) {
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
            'code' => 'That access code is incorrect.',
        ]);
    }
}
