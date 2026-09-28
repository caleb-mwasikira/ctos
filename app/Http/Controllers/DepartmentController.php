<?php

namespace App\Http\Controllers;

use App\Enums\DepartmentRole;
use App\Http\Requests\StoreDepartmentRequest;
use App\Models\Department;
use App\Models\Node;
use App\Models\Organization;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Collection;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Gate;
use Illuminate\View\View;

class DepartmentController extends Controller
{
    /**
     * List the departments the user has joined, across every organization,
     * optionally narrowed down by a search on the department name.
     */
    public function index(Request $request): View
    {
        $search = trim($request->string('search')->toString());

        return view('departments.index', [
            'search' => $search,
            'departments' => $request->user()->departments()
                ->with('organization')
                ->when($search !== '', fn (Builder $departments) => $departments->nameContains($search))
                ->orderBy('departments.name')
                ->orderBy('departments.id')
                ->paginate(20)
                ->withQueryString(),
        ]);
    }

    public function store(StoreDepartmentRequest $request, Organization $organization): RedirectResponse
    {
        $name = $request->validated('name');

        [$department, $accessCode] = DB::transaction(function () use ($request, $organization, $name) {
            $department = $organization->departments()->create([
                'name' => $name,
                'slug' => Department::uniqueSlugFor($name),
                'access_code' => bin2hex(random_bytes(16)),
            ]);

            $department->members()->attach($request->user()->id, ['role' => DepartmentRole::Admin->value]);

            return [$department, $department->regenerateAccessCode()];
        });

        return redirect()
            ->route('departments.show', $department)
            ->with('access_code', $accessCode);
    }

    /**
     * Rename the department. Its slug (its ID people join it with, and the folder name the
     * desktop and Android apps sync it under) is untouched.
     */
    public function update(Request $request, Department $department): RedirectResponse
    {
        Gate::authorize('manage', $department);

        $name = $request->validateWithBag('renameDepartment', ['name' => ['required', 'string', 'max:100']])['name'];
        $department->update(['name' => $name]);

        return back()->with('status', "Renamed to \"{$name}\".");
    }

    public function show(Request $request, Department $department): View
    {
        Gate::authorize('view', $department);

        $department->load('organization');
        $canManage = Gate::allows('manage', $department);

        // The folder being looked at, from ?path=. Folders come first, then files, each by name.
        $path = $this->pathFromRequest($request, $department);

        return view('departments.show', [
            'path' => $path,
            'nodes' => $this->nodesIn($department, $path),
            'department' => $department,
            'members' => $department->activeMembers()->orderBy('name')->orderBy('users.id')->get(),
            'invitationsCount' => $canManage ? $department->invitations()->count() : 0,
            'canManage' => $canManage,
        ]);
    }

    /**
     * Just the file list, for the department page to poll (resources/js/app.js) so a file someone
     * else adds or changes elsewhere shows up here without a reload. It answers only that poll
     * (marked by its own header): a plain visit β€” typed in, bookmarked, opened from history β€” gets
     * sent to the real page instead of a bare, unstyled fragment with no head or CSS.
     */
    public function explorer(Request $request, Department $department): View|RedirectResponse
    {
        Gate::authorize('view', $department);

        $path = $this->pathFromRequest($request, $department);

        if (! $request->hasHeader('X-Explorer-Poll')) {
            return redirect()->route('departments.show', ['department' => $department, 'path' => $path ?: null]);
        }

        return view('departments.partials.explorer', [
            'path' => $path,
            'nodes' => $this->nodesIn($department, $path),
            'department' => $department,
        ]);
    }

    /** The folder being looked at, from ?path=, checked against the department's own folders. */
    private function pathFromRequest(Request $request, Department $department): string
    {
        $path = trim($request->string('path')->toString(), '/');
        abort_if($path !== '' && ! Node::isValidPath($path), 404);
        abort_if($path !== '' && ! $department->nodes()->live()->where('type', Node::FOLDER)->where('path', $path)->exists(), 404);

        return $path;
    }

    /** The folders then files directly inside $path, each alphabetically. */
    private function nodesIn(Department $department, string $path): Collection
    {
        return $department->nodes()->live()
            ->where('parent_path', $path)
            ->orderByRaw("case when type = 'folder' then 0 else 1 end")
            ->orderByRaw('lower(name)')
            ->orderBy('id')
            ->get();
    }
}
