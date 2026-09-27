<x-app-layout :title="$department->name.' invitations'">
    <x-page-header
        title="Invitations"
        :crumbs="['Dashboard' => route('dashboard'), $department->organization->name => route('organizations.show', $department->organization), $department->name => route('departments.show', $department)]"
        subtitle="They get access once they sign up and confirm their email."
    >
        <x-slot:actions>
            <x-button type="button" data-dialog-open="share-department-dialog"><x-icon name="mail" class="size-4" /> Invite by email</x-button>
        </x-slot:actions>
    </x-page-header>

    @if ($invitations->isEmpty())
        <x-card class="flex flex-col items-center py-14 text-center">
            <span class="flex size-14 items-center justify-center rounded-2xl bg-slate-100 text-slate-400 dark:bg-slate-800"><x-icon name="mail" class="size-7" /></span>
            <h2 class="mt-5 text-lg font-semibold">No pending invitations</h2>
            <p class="mt-2 max-w-sm text-sm text-slate-600 dark:text-slate-400">Invite someone by email and it will show up here until they join.</p>
            <x-button type="button" data-dialog-open="share-department-dialog" size="sm" class="mt-6"><x-icon name="mail" class="size-4" /> Invite by email</x-button>
        </x-card>
    @else
        <x-card class="divide-y divide-slate-100 p-0 dark:divide-slate-800">
            @foreach ($invitations as $invitation)
                <div class="flex items-center justify-between gap-3 p-5">
                    <span class="min-w-0 truncate text-sm">{{ $invitation->email }}</span>
                    <form method="POST" action="{{ route('departments.invitations.destroy', [$department, $invitation]) }}">
                        @csrf
                        @method('DELETE')
                        <button type="submit" class="text-sm font-medium text-red-600 hover:text-red-500 dark:text-red-400">Withdraw</button>
                    </form>
                </div>
            @endforeach
        </x-card>
    @endif

    <x-modal id="share-department-dialog" title="Invite to {{ $department->name }}" icon="mail" :open="$errors->has('email')"
        description="Enter an email address. We email them, and they get access to this department. They do not need an account yet: they can sign up with that address.">
        <form method="POST" action="{{ route('departments.invitations.store', $department) }}" class="space-y-5">
            @csrf
            <x-field name="email" label="Email address" type="email" placeholder="colleague@example.com" required autofocus />
            <x-button class="w-full"><x-icon name="mail" class="size-4" /> Send invitation</x-button>
        </form>
    </x-modal>
</x-app-layout>
