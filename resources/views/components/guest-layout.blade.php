@props(['title' => null, 'heading', 'subheading' => null])

<!DOCTYPE html>
<html lang="{{ str_replace('_', '-', app()->getLocale()) }}">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>{{ $title ? $title.' · ' : '' }}{{ config('app.name') }}</title>
        @fonts
        @vite(['resources/css/app.css', 'resources/js/app.js'])
    </head>
    <body class="bg-white font-sans text-slate-900 antialiased dark:bg-slate-950 dark:text-slate-100">
        <div class="grid min-h-screen lg:grid-cols-2">
            <div class="flex flex-col px-6 py-8 sm:px-12">
                <a href="{{ route('home') }}" aria-label="{{ config('app.name') }} home"><x-logo /></a>

                <main class="flex flex-1 items-center py-12">
                    <div class="mx-auto w-full max-w-sm">
                        <h1 class="text-3xl font-semibold tracking-tight">{{ $heading }}</h1>
                        @if ($subheading)
                            <p class="mt-2 text-sm text-slate-600 dark:text-slate-400">{{ $subheading }}</p>
                        @endif

                        @if (session('status'))
                            <div role="status" class="mt-6 flex gap-3 rounded-xl bg-emerald-50 p-4 text-sm text-emerald-800 ring-1 ring-inset ring-emerald-200 dark:bg-emerald-500/10 dark:text-emerald-200 dark:ring-emerald-500/30">
                                <x-icon name="check" class="mt-0.5 size-4 shrink-0" />
                                <p>{{ session('status') }}</p>
                            </div>
                        @endif

                        <div class="mt-8">
                            {{ $slot }}
                        </div>
                    </div>
                </main>
            </div>

            <aside class="relative hidden overflow-hidden bg-brand-950 lg:block" aria-hidden="true">
                <div class="absolute inset-0 bg-[radial-gradient(60%_50%_at_70%_0%,var(--color-brand-600),transparent),radial-gradient(50%_40%_at_10%_100%,var(--color-brand-800),transparent)] opacity-80"></div>
                <div class="absolute inset-0 bg-[linear-gradient(to_right,rgb(255_255_255/0.05)_1px,transparent_1px),linear-gradient(to_bottom,rgb(255_255_255/0.05)_1px,transparent_1px)] bg-size-[48px_48px] mask-[radial-gradient(70%_70%_at_50%_40%,black,transparent)]"></div>

                <div class="relative flex h-full flex-col justify-center px-16">
                    <h2 class="max-w-md text-4xl font-semibold leading-tight tracking-tight text-white">One shared folder, on every machine.</h2>
                    <ul class="mt-10 space-y-6">
                        @foreach ([
                            ['building', 'Organise by department', 'Give every team its own space behind its own access code.'],
                            ['sync', 'Always in sync', 'Edit a file once and it updates everywhere.'],
                            ['lock', 'Private by default', 'Only members of a department can see its files.'],
                        ] as [$icon, $title, $text])
                            <li class="flex gap-4">
                                <span class="flex size-10 shrink-0 items-center justify-center rounded-xl bg-white/10 text-brand-200 ring-1 ring-white/15">
                                    <x-icon :name="$icon" />
                                </span>
                                <div>
                                    <p class="font-medium text-white">{{ $title }}</p>
                                    <p class="mt-0.5 text-sm text-brand-200/80">{{ $text }}</p>
                                </div>
                            </li>
                        @endforeach
                    </ul>
                </div>
            </aside>
        </div>
    </body>
</html>
