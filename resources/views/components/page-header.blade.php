@props(['title', 'subtitle' => null, 'crumbs' => []])

<div class="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
    <div class="min-w-0">
        @if ($crumbs)
            <nav aria-label="Breadcrumb" class="mb-3 flex items-center gap-1.5 text-sm text-slate-500">
                @foreach ($crumbs as $label => $url)
                    <a href="{{ $url }}" class="hover:text-slate-900 dark:hover:text-white">{{ $label }}</a>
                    <x-icon name="chevron-right" class="size-3.5" />
                @endforeach
                <span class="truncate font-medium text-slate-900 dark:text-white" aria-current="page">{{ $title }}</span>
            </nav>
        @endif
        <h1 class="truncate text-3xl font-semibold tracking-tight">{{ $title }}</h1>
        @if ($subtitle)
            <div class="mt-2 text-slate-600 dark:text-slate-400">{{ $subtitle }}</div>
        @endif
    </div>

    @isset($actions)
        <div class="flex shrink-0 items-center gap-3">{{ $actions }}</div>
    @endisset
</div>
