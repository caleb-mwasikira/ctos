@props(['eyebrow', 'title', 'badge' => null, 'reverse' => false])

<div class="grid items-center gap-12 lg:grid-cols-2 lg:gap-20">
    <div class="{{ $reverse ? 'lg:order-2' : '' }}">
        <div class="flex items-center gap-3">
            <p class="text-sm font-semibold text-brand-600 dark:text-brand-300">{{ $eyebrow }}</p>
            @if ($badge)
                <x-badge tone="soon">{{ $badge }}</x-badge>
            @endif
        </div>
        <h2 class="mt-2 text-3xl font-semibold tracking-tight text-balance sm:text-4xl">{{ $title }}</h2>
        {{ $slot }}
    </div>

    <div class="{{ $reverse ? 'lg:order-1' : '' }}">
        <div class="rounded-3xl bg-linear-to-br from-brand-50 via-white to-slate-50 p-4 ring-1 ring-slate-200 sm:p-8 dark:from-brand-950/60 dark:via-slate-900 dark:to-slate-900 dark:ring-slate-800">
            {{ $illustration }}
        </div>
    </div>
</div>
