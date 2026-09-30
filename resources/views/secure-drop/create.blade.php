<x-app-layout :title="'Send · '.$department->name">
    <x-page-header
        title="Send, encrypted"
        :crumbs="['Dashboard' => route('dashboard'), 'Secure Drop' => route('secure-drop.index')]"
        :subtitle="'To members of '.$department->name.' who have a Secure Drop key. Everything is encrypted on this device before it is sent.'"
    />

    @if ($members->isEmpty())
        <x-card class="flex flex-col items-center py-14 text-center">
            <span class="flex size-14 items-center justify-center rounded-2xl bg-slate-100 text-slate-400 dark:bg-slate-800"><x-icon name="lock" class="size-7" /></span>
            <h2 class="mt-5 text-lg font-semibold">Nobody is in this department yet</h2>
        </x-card>
    @else
        <x-card class="max-w-2xl">
            <form data-send-drop-form data-recipients-url="{{ route('secure-drop.store', $department) }}">
                @csrf
                <div class="space-y-6">
                    <div>
                        <label for="drop-files" class="block text-sm font-medium text-slate-700 dark:text-slate-300">Files</label>
                        <div class="mt-1.5 rounded-xl border-2 border-dashed border-slate-300 p-6 text-center dark:border-slate-700">
                            <input id="drop-files" type="file" multiple webkitdirectory class="hidden" data-send-drop-files-folder>
                            <input type="file" multiple class="hidden" data-send-drop-files>
                            <p class="text-sm text-slate-600 dark:text-slate-400">
                                <button type="button" data-send-drop-choose-files class="font-medium text-brand-600 hover:text-brand-500 dark:text-brand-300">Choose files</button>
                                or
                                <button type="button" data-send-drop-choose-folder class="font-medium text-brand-600 hover:text-brand-500 dark:text-brand-300">choose a folder</button>
                            </p>
                            <p data-send-drop-selection class="mt-2 hidden text-sm font-medium text-slate-700 dark:text-slate-300"></p>
                        </div>
                    </div>

                    <fieldset>
                        <legend class="block text-sm font-medium text-slate-700 dark:text-slate-300">Send to</legend>
                        <p class="mt-1 text-xs text-slate-500 dark:text-slate-400">Every member of {{ $department->name }}; only those with a Secure Drop key can be picked.</p>
                        <div class="mt-2 space-y-2">
                            @foreach ($members as $member)
                                @if ($member['has_key'])
                                    <label class="flex items-center gap-3 rounded-lg border border-slate-200 px-3.5 py-2.5 text-sm dark:border-slate-800">
                                        <input
                                            type="checkbox"
                                            class="size-4 rounded border-slate-300 text-brand-600 focus:ring-brand-500 dark:border-slate-700 dark:bg-slate-900"
                                            data-recipient-id="{{ $member['id'] }}"
                                            data-recipient-public-key="{{ $member['public_key'] }}"
                                            {{ $member['id'] === auth()->id() ? 'checked disabled' : '' }}
                                        >
                                        <span class="min-w-0 flex-1">
                                            <span class="block truncate font-medium">{{ $member['name'] }}{{ $member['id'] === auth()->id() ? ' (you)' : '' }}</span>
                                            <span class="block truncate font-mono text-xs text-slate-400">{{ Str::substr($member['fingerprint'], 0, 16) }}…</span>
                                        </span>
                                    </label>
                                @else
                                    <div class="flex items-center gap-3 rounded-lg border border-dashed border-slate-200 px-3.5 py-2.5 text-sm opacity-60 dark:border-slate-700">
                                        <input type="checkbox" disabled class="size-4 rounded border-slate-300 dark:border-slate-700 dark:bg-slate-900">
                                        <span class="min-w-0 flex-1">
                                            <span class="block truncate font-medium">{{ $member['name'] }}</span>
                                            <span class="block truncate text-xs text-slate-400">No Secure Drop key yet</span>
                                        </span>
                                    </div>
                                @endif
                            @endforeach
                        </div>
                    </fieldset>

                    <p data-send-drop-error class="hidden rounded-lg bg-red-50 p-3 text-sm text-red-700 dark:bg-red-500/10 dark:text-red-300"></p>
                    <p data-send-drop-progress class="hidden text-sm text-slate-500"></p>

                    <x-button type="submit" class="w-full" data-send-drop-submit>
                        <span data-send-drop-idle class="contents"><x-icon name="lock" class="size-4" /> Encrypt and send</span>
                        <span data-send-drop-busy class="hidden">Encrypting…</span>
                    </x-button>
                </div>
            </form>
        </x-card>
    @endif

    @push('scripts')
        @vite(['resources/js/secure-drop.js'])
    @endpush
</x-app-layout>
