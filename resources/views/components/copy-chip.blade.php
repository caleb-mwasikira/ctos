@props(['text'])

<button
    type="button"
    data-copy="{{ $text }}"
    title="Copy"
    {{ $attributes->merge(['class' => 'inline-flex items-center gap-1.5 rounded-md bg-slate-100 px-2 py-1 font-mono text-xs text-slate-700 hover:bg-slate-200 dark:bg-slate-800 dark:text-slate-300 dark:hover:bg-slate-700']) }}
>
    <span>{{ $text }}</span>
    <span data-copy-label class="font-sans text-[11px] text-brand-600 dark:text-brand-300">Copy</span>
</button>
