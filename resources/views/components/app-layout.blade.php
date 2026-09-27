@props(['title' => null])

<!DOCTYPE html>
<html lang="{{ str_replace('_', '-', app()->getLocale()) }}">
    <head>
        <meta charset="utf-8">
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <title>{{ $title ? $title.' · ' : '' }}{{ config('app.name') }}</title>
        @fonts
        @vite(['resources/css/app.css', 'resources/js/app.js'])
    </head>
    <body class="min-h-screen bg-slate-50 font-sans text-slate-900 antialiased dark:bg-slate-950 dark:text-slate-100">
        <a href="#content" class="sr-only focus:not-sr-only focus:absolute focus:left-4 focus:top-4 focus:z-50 focus:rounded-lg focus:bg-white focus:px-4 focus:py-2 focus:text-sm focus:shadow-lg">Skip to content</a>

        <header class="sticky top-0 z-40 border-b border-slate-200 bg-white/90 backdrop-blur-lg dark:border-slate-800 dark:bg-slate-900/90">
            <nav class="mx-auto flex h-16 max-w-7xl items-center justify-between gap-4 px-4 sm:px-6" aria-label="Main">
                <div class="flex items-center gap-3">
                    {{-- Opens the mobile drawer: the sections that sit in the sidebar on a wider screen --}}
                    <button type="button" data-dialog-open="mobile-menu-dialog" class="flex size-10 items-center justify-center rounded-full text-slate-600 hover:bg-slate-100 lg:hidden dark:text-slate-300 dark:hover:bg-slate-800" aria-label="Open menu">
                        <x-icon name="menu" class="size-5" />
                    </button>
                    <a href="{{ route('dashboard') }}" aria-label="Dashboard"><x-logo /></a>
                </div>

                <div class="flex items-center gap-1">
                    {{-- Opens the search popup --}}
                    <button type="button" data-dialog-open="search-dialog" class="flex size-10 items-center justify-center rounded-full text-slate-600 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800" aria-label="Search departments" title="Search departments">
                        <x-icon name="search" class="size-5" />
                    </button>

                <details class="relative" data-menu>
                    <summary class="flex cursor-pointer items-center gap-2.5 rounded-full py-1 pl-1 pr-3 hover:bg-slate-100 dark:hover:bg-slate-800">
                        <span class="flex size-8 items-center justify-center rounded-full bg-brand-100 text-sm font-semibold text-brand-700 dark:bg-brand-500/20 dark:text-brand-200">{{ Str::upper(Str::substr(auth()->user()->name, 0, 1)) }}</span>
                        <span class="hidden text-sm font-medium sm:block">{{ auth()->user()->name }}</span>
                    </summary>
                    <div class="absolute right-0 top-12 w-60 rounded-2xl border border-slate-200 bg-white p-2 shadow-xl dark:border-slate-800 dark:bg-slate-900">
                        <div class="px-3 py-2">
                            <p class="truncate text-sm font-medium">{{ auth()->user()->name }}</p>
                            <p class="truncate text-xs text-slate-500">{{ auth()->user()->email }}</p>
                        </div>
                        <form method="POST" action="{{ route('logout') }}" class="border-t border-slate-200 pt-1 dark:border-slate-800">
                            @csrf
                            <button type="submit" class="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm text-slate-700 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800">
                                <x-icon name="logout" class="size-4" /> Log out
                            </button>
                        </form>
                    </div>
                </details>
                </div>
            </nav>
        </header>

        <div class="mx-auto flex max-w-7xl items-start gap-8 px-4 sm:px-6">
            {{-- Sidebar: every section, always in the same place, on a wide enough screen --}}
            <aside class="sticky top-16 hidden w-56 shrink-0 py-10 lg:block">
                <x-nav-links />
            </aside>

            <main id="content" class="min-w-0 flex-1 py-10">
                @if (session('status'))
                    <div role="status" class="mb-8 flex gap-3 rounded-2xl bg-emerald-50 p-4 text-sm text-emerald-800 ring-1 ring-inset ring-emerald-200 dark:bg-emerald-500/10 dark:text-emerald-200 dark:ring-emerald-500/30">
                        <x-icon name="check" class="mt-0.5 size-4 shrink-0" />
                        <p>{{ session('status') }}</p>
                    </div>
                @endif

                @if (session('upload_errors'))
                    <div role="alert" class="mb-8 flex gap-3 rounded-2xl bg-red-50 p-4 text-sm text-red-800 ring-1 ring-inset ring-red-200 dark:bg-red-500/10 dark:text-red-200 dark:ring-red-500/30">
                        <x-icon name="x" class="mt-0.5 size-4 shrink-0" />
                        <ul class="list-inside list-disc space-y-1">
                            @foreach (session('upload_errors') as $message)
                                <li>{{ $message }}</li>
                            @endforeach
                        </ul>
                    </div>
                @endif

                @if (session('access_code'))
                    <div class="mb-8 flex flex-col gap-4 rounded-2xl border border-amber-300 bg-amber-50 p-5 sm:flex-row sm:items-center sm:justify-between dark:border-amber-500/40 dark:bg-amber-500/10">
                        <div class="flex items-start gap-3">
                            <x-icon name="key" class="mt-0.5 size-5 shrink-0 text-amber-600 dark:text-amber-300" />
                            <div>
                                <p class="text-sm font-semibold text-amber-900 dark:text-amber-200">Your new access code</p>
                                <p class="text-sm text-amber-800 dark:text-amber-300/80">Share it with your team. You can copy it again from the department page whenever you need it.</p>
                            </div>
                        </div>
                        <button type="button" data-copy="{{ session('access_code') }}" class="inline-flex items-center justify-center gap-3 rounded-xl bg-white px-4 py-2.5 shadow-sm ring-1 ring-amber-300 hover:bg-amber-50 dark:bg-slate-900 dark:ring-amber-500/40">
                            <span class="font-mono text-xl font-semibold tracking-[0.25em] text-slate-900 dark:text-white">{{ session('access_code') }}</span>
                            <span data-copy-label class="text-xs font-medium text-brand-600 dark:text-brand-300">Copy</span>
                        </button>
                    </div>
                @endif

                {{ $slot }}
            </main>
        </div>

        {{-- The sidebar's sections, plus the account info and log out normally reached from the
             avatar menu, in one slide-in drawer: a narrow screen has no room for both a separate
             navbar menu and a sidebar. --}}
        <dialog
            id="mobile-menu-dialog"
            aria-label="Menu"
            class="m-0 ml-0 mr-auto h-dvh max-h-none w-full max-w-xs rounded-none border-0 bg-white p-0 text-slate-900 shadow-2xl backdrop:bg-slate-950/60 backdrop:backdrop-blur-sm lg:hidden dark:bg-slate-900 dark:text-slate-100"
        >
            <div class="flex h-full flex-col p-5">
                <div class="flex items-center justify-between">
                    <x-logo />
                    <form method="dialog">
                        <button class="flex size-9 items-center justify-center rounded-lg text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800" aria-label="Close menu">
                            <x-icon name="x" class="size-5" />
                        </button>
                    </form>
                </div>

                <x-nav-links class="mt-6 flex-1 space-y-1" />

                <div class="border-t border-slate-200 pt-4 dark:border-slate-800">
                    <div class="flex items-center gap-3 px-3">
                        <span class="flex size-9 shrink-0 items-center justify-center rounded-full bg-brand-100 text-sm font-semibold text-brand-700 dark:bg-brand-500/20 dark:text-brand-200">{{ Str::upper(Str::substr(auth()->user()->name, 0, 1)) }}</span>
                        <span class="min-w-0">
                            <span class="block truncate text-sm font-medium">{{ auth()->user()->name }}</span>
                            <span class="block truncate text-xs text-slate-500">{{ auth()->user()->email }}</span>
                        </span>
                    </div>
                    <form method="POST" action="{{ route('logout') }}" class="mt-2">
                        @csrf
                        <button type="submit" class="flex w-full items-center gap-2 rounded-lg px-3 py-2 text-sm text-slate-700 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800">
                            <x-icon name="logout" class="size-4" /> Log out
                        </button>
                    </form>
                </div>
            </div>
        </dialog>

        {{-- Search popup: finds departments by name across every organization --}}
        <dialog
            id="search-dialog"
            aria-label="Search departments"
            class="mx-auto mb-auto mt-[12vh] w-full max-w-lg rounded-2xl border-0 bg-white p-0 text-slate-900 shadow-2xl backdrop:bg-slate-950/60 backdrop:backdrop-blur-sm dark:bg-slate-900 dark:text-slate-100"
        >
            <form method="GET" action="{{ route('departments.index') }}" role="search" class="p-4 sm:p-5">
                <label for="search" class="sr-only">Search departments</label>
                <div class="relative">
                    <x-icon name="search" class="pointer-events-none absolute left-4 top-1/2 size-5 -translate-y-1/2 text-slate-400" />
                    <input
                        id="search"
                        name="search"
                        type="search"
                        value="{{ request('search') }}"
                        placeholder="Search departments by name"
                        autocomplete="off"
                        autofocus
                        class="block w-full rounded-xl border-0 bg-slate-100 py-3.5 pl-12 pr-4 text-base placeholder:text-slate-500 focus:bg-white focus:ring-2 focus:ring-brand-500 dark:bg-slate-800 dark:focus:bg-slate-900"
                    >
                </div>
                <div class="mt-4 flex items-center justify-between gap-4">
                    <p class="text-xs text-slate-500">Searches every organization you belong to.</p>
                    <x-button size="sm">Search</x-button>
                </div>
            </form>
        </dialog>
    </body>
</html>
