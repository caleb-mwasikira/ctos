<?php

namespace App\Http\Controllers;

use App\Actions\JoinDepartmentAction;
use App\Http\Requests\JoinDepartmentRequest;
use App\Models\Department;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Gate;
use Illuminate\View\View;

class DepartmentMembershipController extends Controller
{
    /**
     * Its own page, asking for confirmation before it actually happens.
     */
    public function edit(Department $department): View
    {
        Gate::authorize('view', $department);

        return view('departments.leave', ['department' => $department]);
    }

    public function store(JoinDepartmentRequest $request, JoinDepartmentAction $joinDepartment): RedirectResponse
    {
        $department = $joinDepartment->handle(
            $request->user(),
            $request->validated('code'),
        );

        return redirect()->route('departments.show', $department);
    }

    public function destroy(Request $request, Department $department): RedirectResponse
    {
        Gate::authorize('view', $department);

        $department->members()->detach($request->user()->id);

        return redirect()->route('dashboard');
    }
}
