<x-app-layout title="Departments">
    <x-page-header title="Departments" :crumbs="['Dashboard' => route('dashboard')]" subtitle="Every department you have joined, across all organizations.">
        <x-slot:actions>
            <x-button type="button" data-dialog-open="join-department-dialog"><x-icon name="key" class="size-4" /> Join a department</x-button>
        </x-slot:actions>
    </x-page-header>

    <section aria-live="polite">
        @if ($departments->isEmpty())
            <x-card class="flex flex-col items-center py-14 text-center">
                <span class="flex size-14 items-center justify-center rounded-2xl bg-slate-100 text-slate-400 dark:bg-slate-800"><x-icon name="search" class="size-7" /></span>
                @if ($search !== '')
                    <h2 class="mt-5 text-lg font-semibold">No departments match "{{ $search }}"</h2>
                    <p class="mt-2 max-w-sm text-sm text-slate-600 dark:text-slate-400">Check the spelling, or try a shorter name.</p>
                    <x-button :href="route('departments.index')" variant="secondary" size="sm" class="mt-6">Clear search</x-button>
                @else
                    <h2 class="mt-5 text-lg font-semibold">No departments</h2>
                    <p class="mt-2 max-w-sm text-sm text-slate-600 dark:text-slate-400">Departments you join with an access code appear here.</p>
                    <x-button type="button" data-dialog-open="join-department-dialog" variant="secondary" size="sm" class="mt-6"><x-icon name="key" class="size-4" /> Join a department</x-button>
                @endif
            </x-card>
        @else
            <p class="mb-3 text-sm text-slate-500">
                {{ $departments->total() }} {{ Str::plural('department', $departments->total()) }}
                @if ($search !== '')
                    matching "{{ $search }}"
                @endif
            </p>

            <x-card class="divide-y divide-slate-100 p-0 dark:divide-slate-800">
                @foreach ($departments as $department)
                    <a href="{{ route('departments.show', $department) }}" class="group flex items-center gap-4 p-5 first:rounded-t-2xl last:rounded-b-2xl hover:bg-slate-50 dark:hover:bg-slate-800/50">
                        <span class="flex size-11 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-300"><x-icon name="folder" /></span>
                        <span class="min-w-0 flex-1">
                            <span class="block truncate font-semibold">{{ $department->name }}</span>
                            <span class="mt-0.5 flex items-center gap-1.5 truncate text-sm text-slate-500"><x-icon name="building" class="size-3.5 shrink-0" /> {{ $department->organization->name }}</span>
                        </span>
                        <x-badge :tone="$department->pivot->role->value === 'admin' ? 'brand' : 'neutral'">{{ Str::ucfirst($department->pivot->role->value) }}</x-badge>
                        <x-icon name="chevron-right" class="size-5 shrink-0 text-slate-400 group-hover:text-brand-600" />
                    </a>
                @endforeach
            </x-card>

            <div class="mt-6">{{ $departments->links() }}</div>
        @endif
    </section>

    <x-modal id="join-department-dialog" title="Join a department" icon="key" :open="$errors->hasAny(['department', 'code'])"
        description="Ask your team for the department ID and its access code.">
        <form method="POST" action="{{ route('department-memberships.store') }}" class="space-y-5">
            @csrf
            <x-field name="department" label="Department ID" placeholder="engineering" required autofocus />
            <x-field name="code" label="Access code" placeholder="K7M2Q9XP4T" autocomplete="off" class="font-mono uppercase tracking-widest" required />
            <x-button class="w-full">Join department</x-button>
        </form>
    </x-modal>
</x-app-layout>
