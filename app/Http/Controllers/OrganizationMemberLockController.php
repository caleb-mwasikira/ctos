<?php

namespace App\Http\Controllers;

use App\Models\Department;
use App\Models\Organization;
use App\Models\OrganizationLock;
use App\Models\User;
use App\Notifications\AccessLocked;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Gate;

class OrganizationMemberLockController extends Controller
{
    /**
     * Lock someone out of every department of the organization at once.
     */
    public function store(Request $request, Organization $organization, User $member): RedirectResponse
    {
        Gate::authorize('manageMembers', $organization);
        $this->ensureLockable($organization, $member);

        $lock = OrganizationLock::firstOrCreate(
            ['organization_id' => $organization->id, 'user_id' => $member->id],
            ['locked_by' => $request->user()->id],
        );

        $status = "{$member->name} is locked out of {$organization->name}.";

        if ($lock->wasRecentlyCreated) {
            $status .= $this->notifyUser($member, new AccessLocked($organization, null, $request->user()))
                ? ' They have been emailed.'
                : ' We could not email them.';
        }

        return back()->with('status', $status);
    }

    public function destroy(Organization $organization, User $member): RedirectResponse
    {
        Gate::authorize('manageMembers', $organization);
        $this->ensureLockable($organization, $member);

        $organization->locks()->where('user_id', $member->id)->delete();

        return back()->with('status', "{$member->name} can open {$organization->name} again.");
    }

    /**
     * Only people who belong to one of the organization's departments can be locked, and never the owner.
     */
    private function ensureLockable(Organization $organization, User $member): void
    {
        abort_if($member->id === $organization->owner_id, 403, 'The owner cannot be locked out.');

        abort_unless(
            Department::where('organization_id', $organization->id)->whereHas('members', fn ($members) => $members->whereKey($member->id))->exists(),
            404
        );
    }
}
