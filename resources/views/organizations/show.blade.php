@php($isOwner = auth()->user()->can('createDepartment', $organization))

<x-app-layout :title="$organization->name">
    <x-page-header :title="$organization->name" :crumbs="['Dashboard' => route('dashboard')]">
        <x-slot:subtitle>
            @if ($isOwner)
                <span class="inline-flex items-center gap-2 text-sm">Organization ID <x-copy-chip :text="$organization->slug" /></span>
            @else
                <span class="text-sm">The departments you have joined in this organization.</span>
            @endif
        </x-slot:subtitle>

        @if ($isOwner)
            <x-slot:actions>
                <x-button type="button" variant="secondary" data-dialog-open="rename-organization-dialog"><x-icon name="pencil" class="size-4" /> Rename</x-button>
                <x-button type="button" data-dialog-open="add-department-dialog"><x-icon name="plus" class="size-4" /> Add department</x-button>
            </x-slot:actions>
        @endif
    </x-page-header>

    <section aria-labelledby="departments-heading">
        <h2 id="departments-heading" class="text-lg font-semibold">Departments</h2>

        @if ($departments->isEmpty())
            <x-card class="mt-4 flex flex-col items-center py-14 text-center">
                <span class="flex size-14 items-center justify-center rounded-2xl bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-300"><x-icon name="folder" class="size-7" /></span>
                <h3 class="mt-5 text-lg font-semibold">No departments yet</h3>
                @if ($isOwner)
                    <p class="mt-2 max-w-sm text-sm text-slate-600 dark:text-slate-400">Add your first department. You will get an access code to share with its members.</p>
                    <x-button type="button" data-dialog-open="add-department-dialog" class="mt-6"><x-icon name="plus" class="size-4" /> Add department</x-button>
                @else
                    <p class="mt-2 max-w-sm text-sm text-slate-600 dark:text-slate-400">You have not joined any departments in this organization.</p>
                @endif
            </x-card>
        @else
            <ul class="mt-4 grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                @foreach ($departments as $department)
                    @php($role = $department->members->first()?->pivot->role)
                    <li>
                        <x-resource-card
                            :href="$role ? route('departments.show', $department) : null"
                            icon="folder"
                            :title="$department->name"
                        >
                            {{ $department->members_count }} {{ Str::plural('member', $department->members_count) }}
                            <x-slot:badge>
                                @if ($role)
                                    <x-badge :tone="$role->value === 'admin' ? 'brand' : 'neutral'">{{ Str::ucfirst($role->value) }}</x-badge>
                                @else
                                    <x-badge tone="soon">Not joined</x-badge>
                                @endif
                            </x-slot:badge>
                        </x-resource-card>
                    </li>
                @endforeach
            </ul>
        @endif
    </section>

    @if ($isOwner)
        <x-modal id="add-department-dialog" title="Add a department" icon="plus" :open="$errors->has('name')"
            description="You are added as its admin and get an access code to share with its members.">
            <form method="POST" action="{{ route('organizations.departments.store', $organization) }}" class="space-y-5">
                @csrf
                <x-field name="name" label="Department name" placeholder="Engineering" required autofocus />
                <x-button class="w-full">Create department</x-button>
            </form>
        </x-modal>

        <x-modal id="rename-organization-dialog" title="Rename {{ $organization->name }}" icon="pencil" :open="$errors->renameOrganization->isNotEmpty()"
            description="Only its name changes. Its ID ({{ $organization->slug }}) stays the same, so nothing that already points to it breaks.">
            <form method="POST" action="{{ route('organizations.update', $organization) }}" class="space-y-5">
                @csrf
                @method('PUT')
                <x-field name="name" label="Organization name" :value="$organization->name" :error-bag="'renameOrganization'" required autofocus />
                <x-button class="w-full">Save</x-button>
            </form>
        </x-modal>
    @endif
</x-app-layout>
