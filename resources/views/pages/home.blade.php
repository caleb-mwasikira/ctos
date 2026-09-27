<x-layout>
    {{-- Hero --}}
    <section class="relative overflow-hidden bg-linear-to-b from-brand-50 via-white to-white dark:from-brand-950/50 dark:via-slate-950 dark:to-slate-950">
        <div class="pointer-events-none absolute inset-0 bg-[linear-gradient(to_right,rgb(100_116_139/0.10)_1px,transparent_1px),linear-gradient(to_bottom,rgb(100_116_139/0.10)_1px,transparent_1px)] bg-size-[56px_56px] mask-[radial-gradient(60%_60%_at_50%_0%,black,transparent)]"></div>

        <div class="relative mx-auto max-w-6xl px-6 pb-20 pt-20 sm:pt-28">
            <div class="mx-auto max-w-3xl text-center">
                <x-badge tone="brand" class="gap-1.5 px-3 py-1"><x-icon name="sync" class="size-3.5" /> Team file sync, organised by department</x-badge>

                <h1 class="mt-6 text-5xl font-semibold tracking-tight text-balance sm:text-6xl">
                    One shared folder,
                    <span class="bg-linear-to-r from-brand-600 to-brand-400 bg-clip-text text-transparent">on every machine.</span>
                </h1>
                <p class="mx-auto mt-6 max-w-2xl text-lg leading-8 text-slate-600 text-pretty dark:text-slate-400">
                    {{ config('app.name') }} keeps your team's files in sync. Edit a file on your laptop and it updates for everyone else.
                    Each department has its own space, protected by its own access code.
                </p>

                <div class="mt-10 flex flex-col items-center justify-center gap-3 sm:flex-row">
                    <x-button :href="route('register')" size="lg">Create an account <x-icon name="arrow-right" class="size-4" /></x-button>
                    <x-button :href="route('how-it-works')" variant="secondary" size="lg">See how it works</x-button>
                </div>
            </div>

            {{-- Product window --}}
            <div class="relative mx-auto mt-16 max-w-5xl" aria-hidden="true">
                <div class="absolute -inset-x-6 -bottom-10 top-10 rounded-[2.5rem] bg-brand-500/20 blur-3xl dark:bg-brand-500/10"></div>

                <div class="relative overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-2xl shadow-brand-900/10 dark:border-slate-800 dark:bg-slate-900">
                    <div class="flex items-center gap-2 border-b border-slate-200 bg-slate-50 px-4 py-3 dark:border-slate-800 dark:bg-slate-900">
                        <span class="size-3 rounded-full bg-red-400"></span>
                        <span class="size-3 rounded-full bg-amber-400"></span>
                        <span class="size-3 rounded-full bg-emerald-400"></span>
                        <span class="mx-auto text-xs font-medium text-slate-500">Acme &middot; Engineering</span>
                        <span class="w-11"></span>
                    </div>

                    <div class="grid md:grid-cols-[13rem_1fr]">
                        <div class="hidden border-r border-slate-200 bg-slate-50/60 p-4 md:block dark:border-slate-800 dark:bg-slate-900">
                            <p class="px-2 text-[11px] font-semibold uppercase tracking-wider text-slate-400">Departments</p>
                            <ul class="mt-3 space-y-1 text-sm">
                                @foreach (['Engineering' => true, 'Design' => false, 'Finance' => false, 'Marketing' => false] as $name => $active)
                                    <li class="flex items-center gap-2.5 rounded-lg px-2.5 py-2 {{ $active ? 'bg-brand-50 font-medium text-brand-700 dark:bg-brand-500/15 dark:text-brand-200' : 'text-slate-600 dark:text-slate-400' }}">
                                        <x-icon name="folder" class="size-4" /> {{ $name }}
                                    </li>
                                @endforeach
                            </ul>
                            <div class="mt-8 flex items-center gap-2 px-2 text-xs text-slate-500">
                                <span class="size-2 rounded-full bg-emerald-500"></span> All devices up to date
                            </div>
                        </div>

                        <div class="p-5 sm:p-6">
                            <div class="flex items-center justify-between">
                                <p class="text-sm font-medium">Engineering <span class="text-slate-400">/ Roadmap</span></p>
                                <div class="flex -space-x-2">
                                    @foreach (['bg-brand-200 text-brand-800' => 'A', 'bg-emerald-200 text-emerald-800' => 'J', 'bg-amber-200 text-amber-800' => 'M'] as $tone => $initial)
                                        <span class="flex size-7 items-center justify-center rounded-full text-xs font-semibold ring-2 ring-white dark:ring-slate-900 {{ $tone }}">{{ $initial }}</span>
                                    @endforeach
                                </div>
                            </div>

                            <div class="mt-5 divide-y divide-slate-100 text-sm dark:divide-slate-800">
                                <div class="grid grid-cols-[1fr_5rem] pb-2 text-xs font-medium text-slate-400 sm:grid-cols-[1fr_6rem_8rem_6rem]">
                                    <span>Name</span><span class="hidden sm:block">Size</span><span class="hidden sm:block">Modified</span><span>Status</span>
                                </div>
                                @foreach ([
                                    ['folder', 'Roadmap 2026', '—', '2 min ago', 'Synced'],
                                    ['file', 'Q3-budget.xlsx', '1.4 MB', '14 min ago', 'Synced'],
                                    ['file', 'Brand-guidelines.pdf', '8.2 MB', 'Just now', 'Syncing'],
                                    ['file', 'Release-checklist.docx', '96 KB', 'Yesterday', 'Synced'],
                                    ['file', 'Meeting-notes.md', '12 KB', 'Yesterday', 'Synced'],
                                ] as [$icon, $name, $size, $modified, $status])
                                    <div class="grid grid-cols-[1fr_5rem] items-center py-3 sm:grid-cols-[1fr_6rem_8rem_6rem]">
                                        <span class="flex min-w-0 items-center gap-3 font-medium">
                                            <x-icon :name="$icon" class="size-5 shrink-0 {{ $icon === 'folder' ? 'text-brand-500' : 'text-slate-400' }}" />
                                            <span class="truncate">{{ $name }}</span>
                                        </span>
                                        <span class="hidden text-slate-500 sm:block">{{ $size }}</span>
                                        <span class="hidden text-slate-500 sm:block">{{ $modified }}</span>
                                        <span class="flex items-center gap-1.5 text-xs {{ $status === 'Synced' ? 'text-emerald-600 dark:text-emerald-400' : 'text-amber-600 dark:text-amber-400' }}">
                                            <span class="size-1.5 rounded-full {{ $status === 'Synced' ? 'bg-emerald-500' : 'bg-amber-500' }}"></span> {{ $status }}
                                        </span>
                                    </div>
                                @endforeach
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </section>

    {{-- Features --}}
    <section id="features" class="scroll-mt-16 bg-white py-24 dark:bg-slate-950">
        <div class="mx-auto max-w-6xl px-6">
            <div class="mx-auto max-w-2xl text-center">
                <p class="text-sm font-semibold text-brand-600 dark:text-brand-300">Features</p>
                <h2 class="mt-2 text-3xl font-semibold tracking-tight text-balance sm:text-4xl">Everything a team needs to share files</h2>
                <p class="mt-4 text-lg text-slate-600 dark:text-slate-400">Simple to set up, easy to control, and out of your way once it is running.</p>
            </div>

            <div class="mt-16 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
                @foreach ([
                    ['building', 'Organizations & departments', 'Create an organization, split it into departments, and keep each team\'s files in its own space.', null],
                    ['key', 'Access codes', 'Each department has a code. Share it with your team once and they can join on their own.', null],
                    ['sync', 'Automatic two-way sync', 'The apps mirror a department\'s files to your device and push your changes back to the cloud.', null],
                    ['bolt', 'Instant updates', 'Changes made on one machine are announced to the others right away, with no manual refresh.', null],
                    ['globe', 'Browse from anywhere', 'Sign in on the website to explore a department\'s folders and download any file.', null],
                    ['lock', 'Encrypted sharing', 'Encrypt a document on your own machine so only the people you choose can ever open it.', 'Coming soon'],
                ] as [$icon, $title, $text, $badge])
                    <x-card class="transition-shadow hover:shadow-md">
                        <div class="flex items-start justify-between">
                            <span class="flex size-11 items-center justify-center rounded-xl bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-300">
                                <x-icon :name="$icon" class="size-6" />
                            </span>
                            @if ($badge)
                                <x-badge tone="soon">{{ $badge }}</x-badge>
                            @endif
                        </div>
                        <h3 class="mt-5 text-lg font-semibold">{{ $title }}</h3>
                        <p class="mt-2 text-sm leading-6 text-slate-600 dark:text-slate-400">{{ $text }}</p>
                    </x-card>
                @endforeach
            </div>
        </div>
    </section>

    {{-- Showcase --}}
    <section class="space-y-28 bg-slate-50 py-24 dark:bg-slate-900/40">
        <div class="mx-auto max-w-6xl px-6">
            <x-showcase eyebrow="Automatic two-way sync" title="Edit anywhere. Find it everywhere.">
                <p class="mt-4 text-lg text-slate-600 dark:text-slate-400">
                    The desktop and Android apps keep a department's files on your device and in step with the cloud. Save a change and it travels to every other machine on its own.
                </p>
                <ul class="mt-8 space-y-4">
                    @foreach ([
                        'Changes flow both ways, from your device to the cloud and back',
                        'Other machines are told the moment something changes',
                        'Your files stay on your device, ready to open offline',
                    ] as $point)
                        <li class="flex gap-3">
                            <span class="mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300"><x-icon name="check" class="size-3.5" /></span>
                            <span class="text-slate-700 dark:text-slate-300">{{ $point }}</span>
                        </li>
                    @endforeach
                </ul>
                <x-slot:illustration><x-illustrations.sync /></x-slot:illustration>
            </x-showcase>
        </div>

        <div class="mx-auto max-w-6xl px-6">
            <x-showcase eyebrow="Department isolation" title="Every department is its own locked room" :reverse="true">
                <p class="mt-4 text-lg text-slate-600 dark:text-slate-400">
                    Members only see the departments they have joined. Everything else stays out of sight, and out of reach, until someone with the right access code lets them in.
                </p>
                <ul class="mt-8 space-y-4">
                    @foreach ([
                        'Each department has its own files and its own access code',
                        'Every request is checked against the person\'s memberships',
                        'Only admins can see a department\'s access code, and can replace it any time',
                    ] as $point)
                        <li class="flex gap-3">
                            <span class="mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300"><x-icon name="check" class="size-3.5" /></span>
                            <span class="text-slate-700 dark:text-slate-300">{{ $point }}</span>
                        </li>
                    @endforeach
                </ul>
                <x-slot:illustration><x-illustrations.isolation /></x-slot:illustration>
            </x-showcase>
        </div>

        <div class="mx-auto max-w-6xl px-6">
            <x-showcase eyebrow="End-to-end encryption" title="Share sensitive files that even we can't read" badge="Coming soon">
                <p class="mt-4 text-lg text-slate-600 dark:text-slate-400">
                    Pick who may open a document. It is encrypted on your own machine with each recipient's public key, so only their private key, kept on their device, can unlock it.
                </p>
                <ul class="mt-8 space-y-4">
                    @foreach ([
                        'Encryption and decryption happen on the devices, never on the server',
                        'Recipients get an email when a document is waiting for them',
                        'The server only ever stores unreadable data',
                    ] as $point)
                        <li class="flex gap-3">
                            <span class="mt-0.5 flex size-6 shrink-0 items-center justify-center rounded-full bg-emerald-100 text-emerald-700 dark:bg-emerald-500/15 dark:text-emerald-300"><x-icon name="check" class="size-3.5" /></span>
                            <span class="text-slate-700 dark:text-slate-300">{{ $point }}</span>
                        </li>
                    @endforeach
                </ul>
                <x-slot:illustration><x-illustrations.encryption /></x-slot:illustration>
            </x-showcase>
        </div>
    </section>

    {{-- Call to action --}}
    <section class="bg-white py-24 dark:bg-slate-950">
        <div class="mx-auto max-w-6xl px-6">
            <div class="relative overflow-hidden rounded-3xl bg-linear-to-br from-brand-600 to-brand-800 px-8 py-16 text-center sm:px-16">
                <div class="pointer-events-none absolute -right-20 -top-20 size-72 rounded-full bg-white/10 blur-3xl"></div>
                <h2 class="relative text-3xl font-semibold tracking-tight text-white text-balance sm:text-4xl">Ready to share your first folder?</h2>
                <p class="relative mx-auto mt-4 max-w-xl text-lg text-brand-100">Create an account, set up an organization and invite your team in a few minutes.</p>
                <div class="relative mt-10 flex flex-col items-center justify-center gap-3 sm:flex-row">
                    <x-button :href="route('register')" variant="light" size="lg">Get started</x-button>
                    <x-button :href="route('downloads')" variant="outline-light" size="lg"><x-icon name="download" class="size-4" /> Get the apps</x-button>
                </div>
            </div>
        </div>
    </section>
</x-layout>
