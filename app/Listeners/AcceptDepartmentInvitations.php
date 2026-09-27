<?php

namespace App\Listeners;

use App\Enums\DepartmentRole;
use App\Models\DepartmentInvitation;
use Illuminate\Auth\Events\Verified;
use Illuminate\Support\Str;

class AcceptDepartmentInvitations
{
    /**
     * Turn the invitations sent to a user's email address into memberships, now that
     * they have proved the address is theirs.
     */
    public function handle(Verified $event): void
    {
        $user = $event->user;

        DepartmentInvitation::with('department')
            ->where('email', Str::lower($user->email))
            ->get()
            ->each(function (DepartmentInvitation $invitation) use ($user) {
                if (! $invitation->department->members()->whereKey($user->id)->exists()) {
                    $invitation->department->members()->attach($user->id, ['role' => DepartmentRole::Member->value]);
                }

                $invitation->delete();
            });
    }
}
