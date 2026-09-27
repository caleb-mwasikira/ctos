<x-layout title="Downloads">
    <section class="bg-linear-to-b from-brand-50 to-white py-20 dark:from-brand-950/50 dark:to-slate-950">
        <div class="mx-auto max-w-3xl px-6 text-center">
            <p class="text-sm font-semibold text-brand-600 dark:text-brand-300">Downloads</p>
            <h1 class="mt-2 text-4xl font-semibold tracking-tight text-balance sm:text-5xl">Take your files with you</h1>
            <p class="mt-5 text-lg text-slate-600 text-pretty dark:text-slate-400">
                The desktop and Android apps keep a department's files on your device and in sync with the cloud. They are still being built.
            </p>
        </div>
    </section>

    <section class="mx-auto max-w-6xl px-6 pb-24">
        <div class="grid gap-6 sm:grid-cols-2 lg:grid-cols-4">
            @foreach ([
                ['monitor', 'Windows', 'Windows 10 and later'],
                ['monitor', 'macOS', 'Intel and Apple silicon'],
                ['monitor', 'Linux', 'Debian, Ubuntu, Fedora'],
                ['phone', 'Android', 'Phones and tablets'],
            ] as [$icon, $platform, $detail])
                <x-card class="flex flex-col items-start">
                    <span class="flex size-12 items-center justify-center rounded-xl bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-300">
                        <x-icon :name="$icon" class="size-6" />
                    </span>
                    <h2 class="mt-5 text-lg font-semibold">{{ $platform }}</h2>
                    <p class="mt-1 text-sm text-slate-500">{{ $detail }}</p>
                    <div class="mt-6 flex w-full items-center justify-between">
                        <x-badge tone="soon">Coming soon</x-badge>
                        <span class="inline-flex cursor-not-allowed items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium text-slate-400 ring-1 ring-inset ring-slate-200 dark:ring-slate-800" aria-disabled="true">
                            <x-icon name="download" class="size-4" /> Download
                        </span>
                    </div>
                </x-card>
            @endforeach
        </div>

        <x-card class="mt-10 flex flex-col items-start gap-4 bg-slate-50 sm:flex-row sm:items-center sm:justify-between dark:bg-slate-900">
            <div class="flex items-start gap-4">
                <span class="flex size-10 shrink-0 items-center justify-center rounded-xl bg-white text-brand-600 ring-1 ring-slate-200 dark:bg-slate-800 dark:ring-slate-700"><x-icon name="globe" /></span>
                <div>
                    <p class="font-semibold">You can already use the website</p>
                    <p class="mt-1 text-sm text-slate-600 dark:text-slate-400">Create an organization, set up departments and join with an access code today.</p>
                </div>
            </div>
            <x-button :href="route('register')">Create an account</x-button>
        </x-card>
    </section>
</x-layout>
