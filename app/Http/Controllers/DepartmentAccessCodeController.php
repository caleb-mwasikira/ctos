<?php

namespace App\Http\Controllers;

use App\Models\Department;
use Illuminate\Http\RedirectResponse;
use Illuminate\Support\Facades\Gate;
use Illuminate\View\View;

class DepartmentAccessCodeController extends Controller
{
    /**
     * Its own page: the current code, and the button to replace it.
     */
    public function index(Department $department): View
    {
        Gate::authorize('manage', $department);

        return view('departments.access-code', ['department' => $department]);
    }

    /**
     * Replace the department's access code. Existing members keep their access.
     */
    public function store(Department $department): RedirectResponse
    {
        Gate::authorize('manage', $department);

        return redirect()
            ->route('departments.access-code.index', $department)
            ->with('access_code', $department->regenerateAccessCode());
    }
}
