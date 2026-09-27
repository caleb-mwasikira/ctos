<?php

namespace App\Http\Controllers;

use App\Models\Department;
use App\Models\User;
use App\Notifications\AccessLocked;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Gate;

class DepartmentMemberLockController extends Controller
{
    /**
     * Lock a member out of this department. They keep their place, so unlocking restores it.
     */
    public function store(Request $request, Department $department, User $member): RedirectResponse
    {
        Gate::authorize('manageMembers', $department);
        DepartmentMemberController::ensureChangeable($department, $member);

        $wasLocked = $member->pivot->locked_at !== null;

        $department->members()->updateExistingPivot($member->id, ['locked_at' => now()]);

        $status = "{$member->name} is locked out of {$department->name}.";

        if (! $wasLocked) {
            $status .= $this->notifyUser($member, new AccessLocked($department->organization, $department, $request->user()))
                ? ' They have been emailed.'
                : ' We could not email them.';
        }

        return back()->with('status', $status);
    }

    public function destroy(Department $department, User $member): RedirectResponse
    {
        Gate::authorize('manageMembers', $department);
        DepartmentMemberController::ensureChangeable($department, $member);

        $department->members()->updateExistingPivot($member->id, ['locked_at' => null]);

        return back()->with('status', "{$member->name} can open {$department->name} again.");
    }
}
