<x-app-layout title="Organizations">
    <x-page-header title="Organizations" :crumbs="['Dashboard' => route('dashboard')]" subtitle="Workspaces you own, and the ones whose departments you have joined.">
        <x-slot:actions>
            <x-button type="button" data-dialog-open="create-organization-dialog"><x-icon name="plus" class="size-4" /> Create organization</x-button>
        </x-slot:actions>
    </x-page-header>

    @if ($organizations->isEmpty())
        <x-card class="flex flex-col items-center py-14 text-center">
            <span class="flex size-14 items-center justify-center rounded-2xl bg-slate-100 text-slate-400 dark:bg-slate-800"><x-icon name="building" class="size-7" /></span>
            <h2 class="mt-5 text-lg font-semibold">No organizations</h2>
            <p class="mt-2 max-w-sm text-sm text-slate-600 dark:text-slate-400">Create one, or join a department with an access code, and it will appear here.</p>
            <x-button type="button" data-dialog-open="create-organization-dialog" size="sm" class="mt-6"><x-icon name="plus" class="size-4" /> Create organization</x-button>
        </x-card>
    @else
        <ul class="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            @foreach ($organizations as $organization)
                @php($isOwner = $organization->owner_id === auth()->id())
                <li>
                    <x-resource-card :href="route('organizations.show', $organization)" icon="building" :title="$organization->name">
                        @if ($isOwner)
                            {{ $organization->departments_count }} {{ Str::plural('department', $organization->departments_count) }}
                        @else
                            {{ $organization->joined_departments_count }} {{ Str::plural('department', $organization->joined_departments_count) }} joined
                        @endif
                        <x-slot:badge>
                            <x-badge :tone="$isOwner ? 'brand' : 'neutral'">{{ $isOwner ? 'Owner' : 'Member' }}</x-badge>
                        </x-slot:badge>
                    </x-resource-card>
                </li>
            @endforeach
        </ul>
    @endif

    <x-modal id="create-organization-dialog" title="Create an organization" icon="building" :open="$errors->has('name')"
        description="Name your company or team. You become its owner and can add departments to it.">
        <form method="POST" action="{{ route('organizations.store') }}" class="space-y-5">
            @csrf
            <x-field name="name" label="Organization name" placeholder="Acme Corp" required autofocus />
            <x-button class="w-full">Create organization</x-button>
        </form>
    </x-modal>
</x-app-layout>
