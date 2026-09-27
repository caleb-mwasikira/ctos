@props(['id', 'title', 'description' => null, 'icon' => null, 'open' => false])

<dialog
    id="{{ $id }}"
    aria-labelledby="{{ $id }}-title"
    @if ($open) data-open @endif
    class="m-auto w-full max-w-md rounded-2xl border-0 bg-white p-0 text-slate-900 shadow-2xl backdrop:bg-slate-950/60 backdrop:backdrop-blur-sm dark:bg-slate-900 dark:text-slate-100"
>
    <div class="p-6 sm:p-8">
        <div class="flex items-start justify-between gap-4">
            <div class="flex items-center gap-3">
                @if ($icon)
                    <span class="flex size-10 shrink-0 items-center justify-center rounded-xl bg-brand-600 text-white"><x-icon :name="$icon" /></span>
                @endif
                <h2 id="{{ $id }}-title" class="text-lg font-semibold">{{ $title }}</h2>
            </div>

            <form method="dialog">
                <button class="flex size-8 items-center justify-center rounded-lg text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800" aria-label="Close">
                    <x-icon name="x" class="size-5" />
                </button>
            </form>
        </div>

        @if ($description)
            <p class="mt-4 text-sm leading-6 text-slate-600 dark:text-slate-400">{{ $description }}</p>
        @endif

        <div class="mt-6">
            {{ $slot }}
        </div>
    </div>
</dialog>
