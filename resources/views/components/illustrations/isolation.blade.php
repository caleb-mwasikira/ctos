<svg viewBox="0 0 480 240" role="img" aria-labelledby="isolation-title" class="h-auto w-full">
    <title id="isolation-title">One department folder is open while two others are locked.</title>

    {{-- Open department --}}
    <rect x="40" y="60" width="120" height="120" rx="28" class="fill-brand-600" />
    <g transform="translate(76 96) scale(2)" fill="none" stroke="white" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
        <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7z" />
    </g>
    <circle cx="152" cy="68" r="16" class="fill-emerald-500 stroke-white dark:stroke-slate-900" stroke-width="4" />
    <path d="M145 68.5l5 5 9-10" fill="none" stroke="white" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" />

    {{-- Locked departments --}}
    @foreach ([180, 320] as $x)
        <rect x="{{ $x }}" y="60" width="120" height="120" rx="28" class="fill-slate-200 dark:fill-slate-800" />
        <g transform="translate({{ $x + 36 }} 96) scale(2)" fill="none" class="stroke-slate-400 dark:stroke-slate-500" stroke-width="1.75" stroke-linecap="round" stroke-linejoin="round">
            <rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" />
        </g>
    @endforeach
</svg>
