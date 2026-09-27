<h2 id="files-heading" class="sr-only">Files</h2>

@php
    $crumbs = [[$department->name, '']];
    $walked = '';
    foreach ($path === '' ? [] : explode('/', $path) as $segment) {
        $walked = ltrim($walked.'/'.$segment, '/');
        $crumbs[] = [$segment, $walked];
    }
@endphp

<x-card class="overflow-hidden p-0">
    <nav aria-label="Folder" class="flex flex-wrap items-center gap-1.5 border-b border-slate-200 px-5 py-3 text-sm dark:border-slate-800">
        <x-icon name="folder" class="size-4 text-brand-500" />
        @foreach ($crumbs as [$label, $crumbPath])
            @unless ($loop->first)
                <x-icon name="chevron-right" class="size-3.5 text-slate-400" />
            @endunless
            @if ($loop->last)
                <span class="font-medium">{{ $label }}</span>
            @else
                <a href="{{ route('departments.show', ['department' => $department, 'path' => $crumbPath ?: null]) }}" class="text-slate-500 hover:text-slate-900 dark:hover:text-white">{{ $label }}</a>
            @endif
        @endforeach

        <button type="button" data-dialog-open="upload-files-dialog" class="ml-auto flex items-center gap-1.5 rounded-lg px-3 py-1.5 text-sm font-medium text-brand-600 hover:bg-brand-50 dark:text-brand-300 dark:hover:bg-brand-500/10">
            <x-icon name="cloud" class="size-4" /> Upload
        </button>
    </nav>

    @if ($nodes->isEmpty())
        <div class="flex flex-col items-center px-6 py-16 text-center">
            <span class="flex size-14 items-center justify-center rounded-2xl bg-slate-100 text-slate-400 dark:bg-slate-800"><x-icon name="cloud" class="size-7" /></span>
            <h3 class="mt-5 text-lg font-semibold">{{ $path === '' ? 'No files yet' : 'This folder is empty' }}</h3>
            <p class="mt-2 max-w-sm text-sm text-slate-600 dark:text-slate-400">Files added in the desktop and Android apps appear here, ready to browse and download.</p>
            <x-button :href="route('downloads')" variant="secondary" size="sm" class="mt-6"><x-icon name="download" class="size-4" /> Get the apps</x-button>
        </div>
    @else
        <div class="grid grid-cols-[1fr_6rem] border-b border-slate-100 px-5 py-2.5 text-xs font-medium text-slate-400 sm:grid-cols-[1fr_6rem_9rem] dark:border-slate-800">
            <span>Name</span><span class="hidden sm:block">Size</span><span>Modified</span>
        </div>

        <ul class="divide-y divide-slate-100 dark:divide-slate-800">
            @foreach ($nodes as $node)
                <li>
                    @if ($node->isFolder())
                        <a href="{{ route('departments.show', ['department' => $department, 'path' => $node->path]) }}" class="grid grid-cols-[1fr_6rem] items-center gap-3 px-5 py-3 text-sm hover:bg-slate-50 sm:grid-cols-[1fr_6rem_9rem] dark:hover:bg-slate-800/50">
                            <span class="flex min-w-0 items-center gap-3 font-medium"><x-icon name="folder" class="size-5 shrink-0 text-brand-500" /> <span class="truncate">{{ $node->name }}</span></span>
                            <span class="hidden text-slate-400 sm:block">&mdash;</span>
                            <span class="text-slate-500">{{ $node->modified_at?->diffForHumans() }}</span>
                        </a>
                    @else
                        <div class="grid grid-cols-[1fr_6rem_2.5rem] items-center gap-3 px-5 py-1.5 text-sm sm:grid-cols-[1fr_6rem_9rem_2.5rem]">
                            <a href="{{ route('departments.files.show', ['department' => $department, 'path' => $node->path]) }}" target="_blank" rel="noopener" class="flex min-w-0 items-center gap-3 rounded-lg py-1.5 font-medium hover:text-brand-600" title="Open {{ $node->name }}">
                                <x-icon name="file" class="size-5 shrink-0 text-slate-400" /> <span class="truncate">{{ $node->name }}</span>
                            </a>
                            <span class="hidden text-slate-500 sm:block">{{ \Illuminate\Support\Number::fileSize($node->size, precision: 1) }}</span>
                            <span class="hidden text-slate-500 sm:block">{{ $node->modified_at?->diffForHumans() }}</span>
                            <a
                                href="{{ route('departments.files.download', ['department' => $department, 'path' => $node->path]) }}"
                                title="Download {{ $node->name }}"
                                aria-label="Download {{ $node->name }}"
                                class="flex size-9 items-center justify-center justify-self-end rounded-lg text-slate-400 hover:bg-slate-100 hover:text-brand-600 dark:hover:bg-slate-800"
                            >
                                <x-icon name="download" class="size-4" />
                            </a>
                        </div>
                    @endif
                </li>
            @endforeach
        </ul>
    @endif
</x-card>
