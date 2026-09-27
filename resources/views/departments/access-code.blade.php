<x-app-layout :title="$department->name.' access code'">
    <x-page-header
        title="Access code"
        :crumbs="['Dashboard' => route('dashboard'), $department->organization->name => route('organizations.show', $department->organization), $department->name => route('departments.show', $department)]"
        subtitle="People join {{ $department->name }} from their dashboard with this code and its department ID."
    />

    <x-card class="max-w-lg">
        <div class="flex items-center gap-3">
            <span class="flex size-10 items-center justify-center rounded-xl bg-amber-50 text-amber-600 dark:bg-amber-500/10 dark:text-amber-300"><x-icon name="key" /></span>
            <h2 class="font-semibold">Current code</h2>
        </div>

        @if ($department->access_code_encrypted)
            <button
                type="button"
                data-copy="{{ $department->access_code_encrypted }}"
                title="Copy the access code"
                class="mt-4 flex w-full items-center justify-between gap-3 rounded-xl bg-slate-50 px-4 py-3 ring-1 ring-inset ring-slate-200 hover:bg-slate-100 dark:bg-slate-800 dark:ring-slate-700 dark:hover:bg-slate-700"
            >
                <span class="font-mono text-lg font-semibold tracking-[0.2em]">{{ $department->access_code_encrypted }}</span>
                <span data-copy-label class="text-xs font-medium text-brand-600 dark:text-brand-300">Copy</span>
            </button>
        @else
            <p class="mt-3 text-sm leading-6 text-slate-600 dark:text-slate-400">
                This code was made before codes could be copied. Generate a new one to see it here.
            </p>
        @endif

        <p class="mt-4 flex flex-wrap items-center gap-2 text-sm text-slate-500">
            Department ID <x-copy-chip :text="$department->slug" />
        </p>

        <div class="mt-5 border-t border-slate-100 pt-5 dark:border-slate-800">
            <x-button type="button" variant="secondary" class="w-full" data-dialog-open="generate-code-dialog"><x-icon name="sync" class="size-4" /> Generate new code</x-button>
        </div>
    </x-card>

    <x-modal id="generate-code-dialog" title="Generate a new access code" icon="sync"
        description="The current code stops working straight away, so anyone you gave it to will need the new one. People who already joined keep their access.">
        <form method="POST" action="{{ route('departments.access-code.store', $department) }}" class="space-y-3">
            @csrf
            <x-button class="w-full"><x-icon name="sync" class="size-4" /> Generate new code</x-button>
            <button type="submit" formmethod="dialog" class="w-full rounded-lg px-4 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800">Cancel</button>
        </form>
    </x-modal>
</x-app-layout>
