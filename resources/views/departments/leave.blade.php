<x-app-layout :title="'Leave '.$department->name">
    <x-page-header
        title="Leave {{ $department->name }}"
        :crumbs="['Dashboard' => route('dashboard'), $department->organization->name => route('organizations.show', $department->organization), $department->name => route('departments.show', $department)]"
    />

    <x-card class="max-w-lg">
        <div class="flex items-center gap-3">
            <span class="flex size-10 items-center justify-center rounded-xl bg-red-50 text-red-600 dark:bg-red-500/10 dark:text-red-400"><x-icon name="logout" /></span>
            <h2 class="font-semibold">Are you sure?</h2>
        </div>
        <p class="mt-3 text-sm leading-6 text-slate-600 dark:text-slate-400">
            You will lose access to its files. Someone who can manage {{ $department->name }} can give it back to you with the access code.
        </p>

        <div class="mt-6 flex gap-3">
            <x-button :href="route('departments.show', $department)" variant="secondary" class="flex-1">Cancel</x-button>
            <form method="POST" action="{{ route('departments.membership.destroy', $department) }}" class="flex-1">
                @csrf
                @method('DELETE')
                <x-button variant="danger" class="w-full">Leave department</x-button>
            </form>
        </div>
    </x-card>
</x-app-layout>
