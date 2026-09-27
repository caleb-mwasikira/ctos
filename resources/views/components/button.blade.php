@props(['variant' => 'primary', 'size' => 'md', 'href' => null])

@php
    $variants = [
        'primary' => 'bg-brand-600 text-white shadow-sm hover:bg-brand-500',
        'secondary' => 'bg-white text-slate-900 shadow-sm ring-1 ring-inset ring-slate-300 hover:bg-slate-50 dark:bg-slate-800 dark:text-white dark:ring-slate-700 dark:hover:bg-slate-700',
        'ghost' => 'text-slate-700 hover:bg-slate-100 dark:text-slate-300 dark:hover:bg-slate-800',
        'danger' => 'bg-white text-red-600 shadow-sm ring-1 ring-inset ring-red-200 hover:bg-red-50 dark:bg-transparent dark:text-red-400 dark:ring-red-500/30 dark:hover:bg-red-500/10',
        'light' => 'bg-white text-brand-700 shadow-sm hover:bg-brand-50',
        'outline-light' => 'text-white ring-1 ring-inset ring-white/30 hover:bg-white/10',
    ];

    $sizes = [
        'sm' => 'px-3 py-1.5 text-sm',
        'md' => 'px-4 py-2 text-sm',
        'lg' => 'px-6 py-3 text-base',
    ];

    $classes = 'inline-flex items-center justify-center gap-2 rounded-lg font-medium transition-colors '.$variants[$variant].' '.$sizes[$size];
@endphp

@if ($href)
    <a href="{{ $href }}" {{ $attributes->merge(['class' => $classes]) }}>{{ $slot }}</a>
@else
    <button {{ $attributes->merge(['type' => 'submit', 'class' => $classes]) }}>{{ $slot }}</button>
@endif
