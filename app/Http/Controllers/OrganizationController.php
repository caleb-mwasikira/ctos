<?php

namespace App\Http\Controllers;

use App\Http\Requests\StoreOrganizationRequest;
use App\Models\Organization;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\Gate;
use Illuminate\View\View;

class OrganizationController extends Controller
{
    /**
     * Every organization the user owns or has joined a department of.
     */
    public function index(Request $request): View
    {
        $user = $request->user();

        return view('organizations.index', [
            'organizations' => Organization::visibleTo($user)
                ->withCount([
                    'departments',
                    'departments as joined_departments_count' => fn (Builder $departments) => $departments->whereHas(
                        'members',
                        fn (Builder $members) => $members->whereKey($user->id)->whereNull('department_user.locked_at')
                    ),
                ])
                ->orderBy('name')
                ->orderBy('id')
                ->get(),
        ]);
    }

    public function store(StoreOrganizationRequest $request): RedirectResponse
    {
        $name = $request->validated('name');

        $organization = $request->user()->ownedOrganizations()->create([
            'name' => $name,
            'slug' => Organization::uniqueSlugFor($name),
        ]);

        return redirect()->route('organizations.show', $organization);
    }

    /**
     * Rename the organization. Its slug (its ID in URLs, and the folder name the desktop and
     * Android apps sync it under) is untouched: only the owner may do even this much.
     */
    public function update(Request $request, Organization $organization): RedirectResponse
    {
        Gate::authorize('update', $organization);

        $name = $request->validateWithBag('renameOrganization', ['name' => ['required', 'string', 'max:100']])['name'];
        $organization->update(['name' => $name]);

        return back()->with('status', "Renamed to \"{$name}\".");
    }

    /**
     * Show the organization's departments: all of them to its owner, and only
     * the ones they have joined to everybody else.
     */
    public function show(Request $request, Organization $organization): View
    {
        Gate::authorize('view', $organization);

        $user = $request->user();

        return view('organizations.show', [
            'organization' => $organization,
            'departments' => $organization->departments()
                ->withCount(['members' => fn (Builder $members) => $members->whereNull('department_user.locked_at')->whereDoesntHave('organizationLocks', fn (Builder $locks) => $locks->where('organization_id', $organization->id))])
                ->with(['members' => fn (BelongsToMany $members) => $members->whereKey($user->id)])
                ->when(
                    $organization->owner_id !== $user->id,
                    fn (Builder $departments) => $departments->whereHas('members', fn (Builder $members) => $members->whereKey($user->id)->whereNull('department_user.locked_at'))
                )
                ->orderBy('name')
                ->orderBy('id')
                ->get(),
        ]);
    }
}
