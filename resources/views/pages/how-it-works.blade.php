<x-layout title="How it works">
    <section class="bg-linear-to-b from-brand-50 to-white py-20 dark:from-brand-950/50 dark:to-slate-950">
        <div class="mx-auto max-w-3xl px-6 text-center">
            <p class="text-sm font-semibold text-brand-600 dark:text-brand-300">How it works</p>
            <h1 class="mt-2 text-4xl font-semibold tracking-tight text-balance sm:text-5xl">From sign-up to synced in four steps</h1>
            <p class="mt-5 text-lg text-slate-600 text-pretty dark:text-slate-400">No servers to run and no invitations to send. Set up your organization, hand out a code, and your team is in.</p>
        </div>
    </section>

    <section class="mx-auto max-w-3xl px-6 pb-24">
        <ol class="relative space-y-10 border-l border-slate-200 pl-10 dark:border-slate-800">
            @foreach ([
                ['building', 'Create an organization', 'Sign up, then create an organization for your company or team. You become its owner and can manage everything inside it.'],
                ['folder', 'Set up departments', 'Add a department for each team, such as Engineering or Finance. Each one has its own files and its own access code.'],
                ['key', 'Join with a code', 'Team members create an account and enter the department\'s access code once. From then on it appears on their dashboard.'],
                ['sync', 'Browse, download and sync', 'Explore files from the website, or install the desktop or Android app to keep a local copy in sync automatically.'],
            ] as $index => [$icon, $title, $text])
                <li class="relative">
                    <span class="absolute -left-[3.75rem] flex size-10 items-center justify-center rounded-full bg-brand-600 text-sm font-semibold text-white ring-8 ring-white dark:ring-slate-950">{{ $index + 1 }}</span>
                    <x-card>
                        <div class="flex items-center gap-3">
                            <x-icon :name="$icon" class="size-5 text-brand-600 dark:text-brand-300" />
                            <h2 class="text-lg font-semibold">{{ $title }}</h2>
                        </div>
                        <p class="mt-3 text-sm leading-6 text-slate-600 dark:text-slate-400">{{ $text }}</p>
                    </x-card>
                </li>
            @endforeach
        </ol>
    </section>

    <section class="bg-slate-50 py-24 dark:bg-slate-900/40">
        <div class="mx-auto max-w-3xl px-6">
            <h2 class="text-center text-3xl font-semibold tracking-tight">Questions, answered</h2>

            <div class="mt-10 divide-y divide-slate-200 rounded-2xl border border-slate-200 bg-white dark:divide-slate-800 dark:border-slate-800 dark:bg-slate-900">
                @foreach ([
                    ['What is an access code?', 'A short code that belongs to one department. Anyone who enters it, with the department\'s ID, becomes a member and can see its files. You can also skip the code and share a department by email.'],
                    ['Can I change a code?', 'Yes. The organization owner and department admins can generate a new code at any time. People who already joined keep their access; only new members need the new code.'],
                    ['Do I need the app?', 'No. You can browse and download files from the website. The desktop and Android apps add automatic two-way sync, so your files are on your machine and always current.'],
                    ['Which platforms are supported?', 'The apps are being built for Windows, macOS, Linux and Android. See the downloads page for the latest.'],
                    ['How are my files protected?', 'Only members of a department can see its files, and only admins can see its access code. End-to-end encrypted sharing, where a document is encrypted on your device for specific people, is on the way.'],
                ] as [$question, $answer])
                    <details class="group p-6">
                        <summary class="flex cursor-pointer items-center justify-between gap-4 font-medium">
                            {{ $question }}
                            <x-icon name="plus" class="size-5 shrink-0 text-slate-400 transition-transform group-open:rotate-45" />
                        </summary>
                        <p class="mt-3 text-sm leading-6 text-slate-600 dark:text-slate-400">{{ $answer }}</p>
                    </details>
                @endforeach
            </div>

            <div class="mt-12 text-center">
                <x-button :href="route('register')" size="lg">Get started</x-button>
            </div>
        </div>
    </section>
</x-layout>
