@props(['title' => null])

@php
    $links = [
        ['Features', route('home').'#features'],
        ['How it works', route('how-it-works')],
        ['Downloads', route('downloads')],
    ];
@endphp

<!DOCTYPE html>
<html lang="{{ str_replace('_', '-', app()->getLocale()) }}">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>{{ $title ? $title.' · ' : '' }}{{ config('app.name') }}</title>
        @fonts
        @vite(['resources/css/app.css', 'resources/js/app.js'])
    </head>
    <body class="min-h-screen bg-white font-sans text-slate-900 antialiased dark:bg-slate-950 dark:text-slate-100">
        <a href="#content" class="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-white focus:px-4 focus:py-2 focus:text-sm focus:shadow-lg">Skip to content</a>

        <header class="sticky top-0 z-40 border-b border-slate-200/70 bg-white/80 backdrop-blur-lg dark:border-slate-800/70 dark:bg-slate-950/80">
            <nav class="mx-auto flex h-16 max-w-6xl items-center justify-between px-6" aria-label="Main">
                <a href="{{ route('home') }}" aria-label="{{ config('app.name') }} home"><x-logo /></a>

                <div class="hidden items-center gap-1 md:flex">
                    @foreach ($links as [$label, $url])
                        <a href="{{ $url }}" class="rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-white">{{ $label }}</a>
                    @endforeach
                </div>

                <div class="hidden items-center gap-2 md:flex">
                    @auth
                        <x-button :href="route('dashboard')" size="sm">Open dashboard</x-button>
                    @else
                        <x-button :href="route('login')" variant="ghost" size="sm">Log in</x-button>
                        <x-button :href="route('register')" size="sm">Get started</x-button>
                    @endauth
                </div>

                <details class="relative md:hidden" data-menu>
                    <summary class="flex size-10 cursor-pointer items-center justify-center rounded-lg text-slate-700 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800" aria-label="Menu">
                        <x-icon name="menu" class="size-6" />
                    </summary>
                    <div class="absolute right-0 top-12 w-64 rounded-2xl border border-slate-200 bg-white p-2 shadow-xl dark:border-slate-800 dark:bg-slate-900">
                        @foreach ($links as [$label, $url])
                            <a href="{{ $url }}" class="block rounded-lg px-3 py-2.5 text-sm font-medium hover:bg-slate-100 dark:hover:bg-slate-800">{{ $label }}</a>
                        @endforeach
                        <div class="mt-2 flex flex-col gap-2 border-t border-slate-200 pt-3 dark:border-slate-800">
                            @auth
                                <x-button :href="route('dashboard')">Open dashboard</x-button>
                            @else
                                <x-button :href="route('login')" variant="secondary">Log in</x-button>
                                <x-button :href="route('register')">Get started</x-button>
                            @endauth
                        </div>
                    </div>
                </details>
            </nav>
        </header>

        <main id="content">
            {{ $slot }}
        </main>

        <footer class="border-t border-slate-200 bg-slate-50 dark:border-slate-800 dark:bg-slate-900/50">
            <div class="mx-auto grid max-w-6xl gap-10 px-6 py-14 md:grid-cols-4">
                <div class="md:col-span-2">
                    <x-logo />
                    <p class="mt-4 max-w-sm text-sm leading-6 text-slate-600 dark:text-slate-400">
                        One shared folder for your whole team. Organised by department, protected by access codes, synced to every machine.
                    </p>
                </div>
                <div>
                    <h2 class="text-sm font-semibold">Product</h2>
                    <ul class="mt-4 space-y-3 text-sm text-slate-600 dark:text-slate-400">
                        <li><a href="{{ route('home') }}#features" class="hover:text-slate-900 dark:hover:text-white">Features</a></li>
                        <li><a href="{{ route('how-it-works') }}" class="hover:text-slate-900 dark:hover:text-white">How it works</a></li>
                        <li><a href="{{ route('downloads') }}" class="hover:text-slate-900 dark:hover:text-white">Downloads</a></li>
                    </ul>
                </div>
                <div>
                    <h2 class="text-sm font-semibold">Account</h2>
                    <ul class="mt-4 space-y-3 text-sm text-slate-600 dark:text-slate-400">
                        <li><a href="{{ route('login') }}" class="hover:text-slate-900 dark:hover:text-white">Log in</a></li>
                        <li><a href="{{ route('register') }}" class="hover:text-slate-900 dark:hover:text-white">Create an account</a></li>
                    </ul>
                </div>
            </div>
            <div class="border-t border-slate-200 dark:border-slate-800">
                <p class="mx-auto max-w-6xl px-6 py-6 text-xs text-slate-500">&copy; {{ date('Y') }} {{ config('app.name') }}. All rights reserved.</p>
            </div>
        </footer>
    </body>
</html>
