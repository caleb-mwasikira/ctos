<?php

namespace App\Policies;

use App\Enums\DepartmentRole;
use App\Models\Department;
use App\Models\User;

class DepartmentPolicy
{
    /**
     * Members (and only members) may open a department and its files. A locked member,
     * or one locked out of the whole organization, may not.
     */
    public function view(User $user, Department $department): bool
    {
        return $user->departments()->whereKey($department->id)->exists();
    }

    /**
     * The organization owner, department admins and department leads may share the department
     * and manage its access code.
     */
    public function manage(User $user, Department $department): bool
    {
        if ($department->organization->owner_id === $user->id) {
            return true;
        }

        return $user->departments()
            ->whereKey($department->id)
            ->wherePivotIn('role', [DepartmentRole::Admin->value, DepartmentRole::Lead->value])
            ->exists();
    }

    /**
     * Only the organization owner assigns department leads and locks members out.
     */
    public function manageMembers(User $user, Department $department): bool
    {
        return $department->organization->owner_id === $user->id;
    }
}
