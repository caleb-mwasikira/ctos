@props(['icon', 'title', 'href' => null])

{{-- A card for an organization or a department: a link when it has an href, plain otherwise. --}}
@php($tag = $href ? 'a' : 'div')

<{{ $tag }}
    @if ($href) href="{{ $href }}" @endif
    {{ $attributes->class([
        'group flex h-full items-center gap-4 rounded-2xl border border-slate-200 bg-white p-5 shadow-sm dark:border-slate-800 dark:bg-slate-900',
        'transition hover:border-brand-300 hover:shadow-md dark:hover:border-brand-500/50' => $href,
    ]) }}
>
    <span class="flex size-12 shrink-0 items-center justify-center rounded-xl bg-brand-50 text-brand-600 dark:bg-brand-500/10 dark:text-brand-300"><x-icon :name="$icon" class="size-6" /></span>
    <span class="min-w-0 flex-1">
        <span class="block truncate font-semibold">{{ $title }}</span>
        <span class="block text-sm text-slate-500">{{ $slot }}</span>
    </span>
    @isset($badge)
        {{ $badge }}
    @endisset
</{{ $tag }}>
