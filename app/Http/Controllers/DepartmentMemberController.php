<?php

namespace App\Http\Controllers;

use App\Enums\DepartmentRole;
use App\Models\Department;
use App\Models\User;
use App\Notifications\DepartmentLeadAssigned;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Gate;
use Illuminate\Validation\Rule;
use Illuminate\View\View;

class DepartmentMemberController extends Controller
{
    /**
     * Every member of the department, locked ones included. Owners, admins and leads can see
     * it; only the owner gets the buttons to change anything.
     */
    public function index(Department $department): View
    {
        Gate::authorize('manage', $department);

        $department->load('organization');

        return view('departments.members', [
            'department' => $department,
            'members' => $department->members()->orderBy('name')->orderBy('users.id')->get(),
            'lockedOutOfOrganization' => $department->organization->locks()->pluck('user_id')->all(),
            'canManageMembers' => Gate::allows('manageMembers', $department),
        ]);
    }

    /**
     * Make a member the department lead, or a regular member again.
     */
    public function update(Request $request, Department $department, User $member): RedirectResponse
    {
        Gate::authorize('manageMembers', $department);
        $this->ensureChangeable($department, $member);

        $role = $request->validate([
            'role' => ['required', Rule::in([DepartmentRole::Lead->value, DepartmentRole::Member->value])],
        ])['role'];

        $wasLead = $member->pivot->role === DepartmentRole::Lead;

        $department->members()->updateExistingPivot($member->id, ['role' => $role]);

        if ($role !== DepartmentRole::Lead->value) {
            return back()->with('status', "{$member->name} is a regular member of {$department->name} again.");
        }

        $status = "{$member->name} is now a lead of {$department->name}.";

        if (! $wasLead) {
            $status .= $this->notifyUser($member, new DepartmentLeadAssigned($department, $request->user()))
                ? ' They have been emailed.'
                : ' We could not email them.';
        }

        return back()->with('status', $status);
    }

    /**
     * The owner and admins keep their own role: they cannot be demoted or locked out here.
     */
    public static function ensureChangeable(Department $department, User $member): void
    {
        abort_if(
            $member->id === $department->organization->owner_id || $member->pivot?->role === DepartmentRole::Admin,
            403,
            'This member cannot be changed.'
        );
    }
}
