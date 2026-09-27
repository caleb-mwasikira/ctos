<svg viewBox="0 0 480 240" role="img" aria-labelledby="encryption-title" class="h-auto w-full">
    <title id="encryption-title">A document is locked before it is sent, so only the person it is for can open it.</title>

    {{-- Document going in --}}
    <rect x="52" y="76" width="68" height="88" rx="12" class="fill-white stroke-slate-400 dark:fill-slate-900 dark:stroke-slate-500" stroke-width="4" />
    @foreach ([100, 118, 136] as $y)
        <rect x="68" y="{{ $y }}" width="{{ $y === 136 ? 20 : 36 }}" height="6" rx="3" class="fill-slate-300 dark:fill-slate-600" />
    @endforeach

    {{-- Lock --}}
    <circle cx="240" cy="120" r="48" class="fill-brand-600" />
    <g transform="translate(204 84) scale(3)" fill="none" stroke="white" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
        <rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" />
    </g>

    {{-- Arrows --}}
    <g fill="none" class="stroke-slate-300 dark:stroke-slate-600" stroke-width="4" stroke-linecap="round" stroke-linejoin="round">
        <path d="M136 120h44m-12-12l12 12-12 12" />
        <path d="M300 120h44m-12-12l12 12-12 12" />
    </g>

    {{-- Document coming out --}}
    <rect x="360" y="76" width="68" height="88" rx="12" class="fill-white stroke-emerald-500 dark:fill-slate-900" stroke-width="4" />
    @foreach ([100, 118, 136] as $y)
        <rect x="376" y="{{ $y }}" width="{{ $y === 136 ? 20 : 36 }}" height="6" rx="3" class="fill-emerald-500" />
    @endforeach
</svg>
