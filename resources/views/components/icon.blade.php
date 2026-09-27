@props(['name'])

<svg
    xmlns="http://www.w3.org/2000/svg"
    viewBox="0 0 24 24"
    fill="none"
    stroke="currentColor"
    stroke-width="1.75"
    stroke-linecap="round"
    stroke-linejoin="round"
    aria-hidden="true"
    {{ $attributes->merge(['class' => 'size-5']) }}
>
    @switch($name)
        @case('folder')
            <path d="M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7z" />
            @break
        @case('file')
            <path d="M7 3h7l5 5v12a1 1 0 0 1-1 1H7a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1z" /><path d="M14 3v5h5" />
            @break
        @case('sync')
            <path d="M20 11a8 8 0 0 0-14.9-3M4 5v4h4M4 13a8 8 0 0 0 14.9 3M20 19v-4h-4" />
            @break
        @case('lock')
            <rect x="5" y="11" width="14" height="10" rx="2" /><path d="M8 11V8a4 4 0 0 1 8 0v3" />
            @break
        @case('key')
            <circle cx="8" cy="15" r="4" /><path d="M11 12l8-8M16 7l3 3M14 9l2 2" />
            @break
        @case('users')
            <circle cx="9" cy="8" r="3.5" /><path d="M2.5 20a6.5 6.5 0 0 1 13 0M16 4.6a3.5 3.5 0 0 1 0 6.8M18 14.2A6.5 6.5 0 0 1 21.5 20" />
            @break
        @case('building')
            <path d="M4 21V5a1 1 0 0 1 1-1h8a1 1 0 0 1 1 1v16M14 9h5a1 1 0 0 1 1 1v11M3 21h18M8 8h2M8 12h2M8 16h2" />
            @break
        @case('download')
            <path d="M12 4v11m0 0l-4-4m4 4l4-4M5 20h14" />
            @break
        @case('monitor')
            <rect x="3" y="4" width="18" height="12" rx="2" /><path d="M8 20h8M12 16v4" />
            @break
        @case('phone')
            <rect x="7" y="2.5" width="10" height="19" rx="2.5" /><path d="M11 18.5h2" />
            @break
        @case('check')
            <path d="M5 12.5l4.5 4.5L19 7.5" />
            @break
        @case('arrow-right')
            <path d="M5 12h14m0 0l-5-5m5 5l-5 5" />
            @break
        @case('chevron-right')
            <path d="M9 6l6 6-6 6" />
            @break
        @case('shield')
            <path d="M12 3l7 3v5c0 4.5-3 8.2-7 10-4-1.8-7-5.5-7-10V6l7-3z" /><path d="M9 12l2 2 4-4" />
            @break
        @case('mail')
            <rect x="3" y="5" width="18" height="14" rx="2" /><path d="M3.5 7l8.5 6 8.5-6" />
            @break
        @case('bolt')
            <path d="M13 3L5 13.5h6L10 21l8-10.5h-6L13 3z" />
            @break
        @case('globe')
            <circle cx="12" cy="12" r="9" /><path d="M3 12h18M12 3c2.5 2.6 3.8 5.6 3.8 9s-1.3 6.4-3.8 9c-2.5-2.6-3.8-5.6-3.8-9S9.5 5.6 12 3z" />
            @break
        @case('plus')
            <path d="M12 5v14M5 12h14" />
            @break
        @case('dots')
            <circle cx="5" cy="12" r="1.4" fill="currentColor" /><circle cx="12" cy="12" r="1.4" fill="currentColor" /><circle cx="19" cy="12" r="1.4" fill="currentColor" />
            @break
        @case('eye')
            <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z" /><circle cx="12" cy="12" r="3" />
            @break
        @case('eye-off')
            <path d="M2 12s3.6-7 10-7 10 7 10 7-3.6 7-10 7S2 12 2 12z" /><circle cx="12" cy="12" r="3" /><path d="M4 4l16 16" />
            @break
        @case('x')
            <path d="M6 6l12 12M18 6L6 18" />
            @break
        @case('menu')
            <path d="M4 7h16M4 12h16M4 17h16" />
            @break
        @case('search')
            <circle cx="11" cy="11" r="6.5" /><path d="M16 16l4.5 4.5" />
            @break
        @case('cloud')
            <path d="M7 18a4 4 0 0 1-.6-7.96A6 6 0 0 1 18 9.5 4.25 4.25 0 0 1 17.5 18H7z" />
            @break
        @case('logout')
            <path d="M14 4h4a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2h-4M10 16l-4-4m0 0l4-4m-4 4h11" />
            @break
        @case('pencil')
            <path d="M4 20l1-4.5L15.5 5 19 8.5 8.5 19 4 20z" /><path d="M13.5 6.5l4 4" />
            @break
        @case('home')
            <path d="M4 11.5L12 4l8 7.5" /><path d="M6 10v9a1 1 0 0 0 1 1h4v-6h2v6h4a1 1 0 0 0 1-1v-9" />
            @break
    @endswitch
</svg>
