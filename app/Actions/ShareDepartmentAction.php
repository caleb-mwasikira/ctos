<?php

namespace App\Actions;

use App\Enums\DepartmentRole;
use App\Models\Department;
use App\Models\User;
use App\Notifications\DepartmentShared;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Notification;
use Illuminate\Support\Str;

class ShareDepartmentAction
{
    /**
     * Give the person behind an email address access to a department, and email them.
     *
     * Someone with a verified account is added to the department straight away. Anyone else
     * (no account yet, or an address that is not verified) gets a pending invitation that
     * turns into access once they verify that address, so an address cannot be claimed by
     * someone who does not own it.
     *
     * @return 'added'|'invited'|'already_member'|'locked'
     */
    public function handle(Department $department, User $inviter, string $email): string
    {
        $email = Str::lower(trim($email));
        $existing = User::where('email', $email)->first();

        if ($existing !== null && $department->isLockedFor($existing)) {
            return 'locked';
        }

        if ($existing !== null && $department->members()->whereKey($existing->id)->exists()) {
            return 'already_member';
        }

        $hasAccess = $existing?->hasVerifiedEmail() ?? false;

        // If the email cannot be sent, nothing is granted either.
        DB::transaction(function () use ($department, $inviter, $email, $existing, $hasAccess) {
            if ($hasAccess) {
                $department->members()->attach($existing->id, ['role' => DepartmentRole::Member->value]);
            } else {
                $department->invitations()->firstOrCreate(
                    ['email' => $email],
                    ['invited_by' => $inviter->id],
                );
            }

            Notification::route('mail', $email)->notify(new DepartmentShared($department, $inviter, $email, $hasAccess));
        });

        return $hasAccess ? 'added' : 'invited';
    }
}
