<?php

namespace App\Policies;

use App\Models\Organization;
use App\Models\User;

class OrganizationPolicy
{
    /**
     * The owner, and anyone with an unlocked place in one of its departments, may open it.
     */
    public function view(User $user, Organization $organization): bool
    {
        return Organization::visibleTo($user)->whereKey($organization->id)->exists();
    }

    public function createDepartment(User $user, Organization $organization): bool
    {
        return $organization->owner_id === $user->id;
    }

    /**
     * Only the owner may rename the organization. Its slug (its ID in URLs, and the folder name
     * the desktop and Android apps sync it under) never changes, renaming or not.
     */
    public function update(User $user, Organization $organization): bool
    {
        return $organization->owner_id === $user->id;
    }

    /**
     * Only the owner may lock people out of the whole organization.
     */
    public function manageMembers(User $user, Organization $organization): bool
    {
        return $organization->owner_id === $user->id;
    }
}
