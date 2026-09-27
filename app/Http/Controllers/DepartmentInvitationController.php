<?php

namespace App\Http\Controllers;

use App\Actions\ShareDepartmentAction;
use App\Http\Requests\ShareDepartmentRequest;
use App\Models\Department;
use App\Models\DepartmentInvitation;
use Illuminate\Http\RedirectResponse;
use Illuminate\Support\Facades\Gate;
use Illuminate\Validation\ValidationException;
use Illuminate\View\View;
use Throwable;

class DepartmentInvitationController extends Controller
{
    /**
     * Its own page: who has been invited but has not joined yet, and the form to invite someone.
     */
    public function index(Department $department): View
    {
        Gate::authorize('manage', $department);

        return view('departments.invitations', [
            'department' => $department,
            'invitations' => $department->invitations()->orderBy('email')->get(),
        ]);
    }

    /**
     * Share the department with an email address.
     */
    public function store(ShareDepartmentRequest $request, Department $department, ShareDepartmentAction $share): RedirectResponse
    {
        $email = $request->validated('email');

        try {
            $result = $share->handle($department, $request->user(), $email);
        } catch (Throwable $exception) {
            report($exception);

            throw ValidationException::withMessages([
                'email' => 'We could not send the email right now. Please try again in a moment.',
            ]);
        }

        if ($result === 'locked') {
            throw ValidationException::withMessages(['email' => 'That person is locked out of this department, so it cannot be shared with them.']);
        }

        if ($result === 'already_member') {
            throw ValidationException::withMessages(['email' => 'That person already has access to this department.']);
        }

        return back()->with('status', $result === 'added'
            ? "{$email} now has access to {$department->name} and has been emailed."
            : "We emailed {$email}. They will get access to {$department->name} as soon as they sign up and confirm that address.");
    }

    /**
     * Withdraw an invitation that has not been accepted yet.
     */
    public function destroy(Department $department, DepartmentInvitation $invitation): RedirectResponse
    {
        Gate::authorize('manage', $department);

        $invitation->delete();

        return back()->with('status', "The invitation for {$invitation->email} was withdrawn.");
    }
}
