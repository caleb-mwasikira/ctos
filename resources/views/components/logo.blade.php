@props(['light' => false])

<span {{ $attributes->merge(['class' => 'inline-flex items-center gap-2.5']) }}>
    <span class="flex size-8 items-center justify-center rounded-lg bg-linear-to-br from-brand-500 to-brand-700 text-white shadow-sm shadow-brand-600/30">
        <x-icon name="folder" class="size-[18px]" />
    </span>
    <span class="text-lg font-semibold tracking-tight {{ $light ? 'text-white' : 'text-slate-900 dark:text-white' }}">{{ config('app.name') }}</span>
</span>
