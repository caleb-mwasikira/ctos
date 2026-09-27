{{-- The app's sections: the desktop sidebar and the mobile drawer both render this same list, so they can never drift apart. --}}
@php
    $items = [
        ['dashboard', route('dashboard'), 'home', 'Dashboard'],
        ['organizations.*', route('organizations.index'), 'building', 'Organizations'],
        ['departments.*', route('departments.index'), 'folder', 'Departments'],
        ['downloads', route('downloads'), 'download', 'Apps'],
    ];
@endphp

<nav {{ $attributes->merge(['class' => 'space-y-1']) }} aria-label="Sections">
    @foreach ($items as [$pattern, $url, $icon, $label])
        @php($active = request()->routeIs($pattern))
        <a
            href="{{ $url }}"
            @if ($active) aria-current="page" @endif
            class="flex items-center gap-3 rounded-xl px-3 py-2.5 text-sm font-medium transition {{ $active ? 'bg-brand-50 text-brand-700 dark:bg-brand-500/10 dark:text-brand-300' : 'text-slate-600 hover:bg-slate-100 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-white' }}"
        >
            <x-icon :name="$icon" class="size-5 shrink-0" />
            {{ $label }}
        </a>
    @endforeach
</nav>
