@php
    $subheading = '"'.$name.'" was sent to you through '.config('app.name')."'s Secure Drop.";
@endphp
<x-guest-layout :title="'Open '.$name" heading="Open a file, encrypted" :subheading="$subheading">
    <div class="space-y-5" data-shared-drop data-shared-token="{{ $token }}">
        <div>
            <label for="shared-drop-password" class="block text-sm font-medium text-slate-700 dark:text-slate-300">The password from your email</label>
            <input id="shared-drop-password" type="password" autocomplete="off" class="mt-1.5 block w-full rounded-lg border-0 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm ring-1 ring-inset ring-slate-300 focus:ring-2 focus:ring-inset focus:ring-brand-500 dark:bg-slate-900 dark:text-white dark:ring-slate-700">
        </div>
        <p data-shared-drop-error class="hidden rounded-lg bg-red-50 p-3 text-sm text-red-700 dark:bg-red-500/10 dark:text-red-300"></p>
        <button type="button" data-shared-drop-submit class="inline-flex w-full items-center justify-center gap-2 rounded-lg bg-brand-600 px-4 py-2 text-sm font-medium text-white hover:bg-brand-500">
            <span data-shared-drop-idle class="contents">Decrypt and download</span>
            <span data-shared-drop-busy class="hidden">Decrypting…</span>
        </button>
    </div>

    <p class="mt-8 border-t border-slate-100 pt-6 text-sm text-slate-500 dark:border-slate-800 dark:text-slate-400">
        Decrypting happens right here in your browser — the password never leaves this device.
        Once you have your own {{ config('app.name') }} account and Secure Drop key,
        <a href="{{ route('register') }}" class="font-medium text-brand-600 hover:text-brand-500 dark:text-brand-300">create one</a>,
        the next file sent to you arrives already yours to open, with no password to keep track of.
    </p>

    @vite(['resources/js/secure-drop.js'])
</x-guest-layout>
