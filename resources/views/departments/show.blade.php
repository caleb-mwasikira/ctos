<x-app-layout :title="$department->name">
    <x-page-header
        :title="$department->name"
        :crumbs="['Dashboard' => route('dashboard'), $department->organization->name => route('organizations.show', $department->organization)]"
    >
        <x-slot:subtitle>
            <span class="inline-flex items-center gap-2 text-sm">
                <x-icon name="building" class="size-4" /> {{ $department->organization->name }}
                <span aria-hidden="true">&middot;</span>
                {{ $members->count() }} {{ Str::plural('member', $members->count()) }}
            </span>
        </x-slot:subtitle>

        @if ($canManage)
            <x-slot:actions>
                <x-button type="button" variant="secondary" data-dialog-open="rename-department-dialog"><x-icon name="pencil" class="size-4" /> Rename</x-button>
                <x-button :href="route('departments.invitations.index', $department)"><x-icon name="mail" class="size-4" /> Share</x-button>
            </x-slot:actions>
        @endif
    </x-page-header>

    <div class="grid gap-8 lg:grid-cols-3">
        {{-- File explorer: refreshes itself every few seconds (resources/js/app.js), so a file
             someone else adds or changes from another device shows up without a reload. --}}
        <section
            class="lg:col-span-2"
            aria-labelledby="files-heading"
            id="department-explorer"
            data-explorer
            data-explorer-url="{{ route('departments.explorer', ['department' => $department, 'path' => $path ?: null]) }}"
        >
            @include('departments.partials.explorer')
        </section>

        {{-- Side panel: self-start so it does not stretch to the explorer's height, which would
             otherwise blow up every h-full card inside it (x-resource-card is meant for a row of
             cards side by side, not a column stacked with space-y). --}}
        <div class="space-y-8 self-start">
            <x-card @class(['relative', 'transition hover:border-brand-300 hover:shadow-md dark:hover:border-brand-500/50' => $canManage])>
                @if ($canManage)
                    <a href="{{ route('departments.members.index', $department) }}" class="flex items-center justify-between after:absolute after:inset-0 after:rounded-2xl">
                        <h2 class="font-semibold">Members</h2>
                        <span class="flex items-center gap-1 text-sm font-medium text-brand-600 dark:text-brand-300">View all <x-icon name="chevron-right" class="size-4" /></span>
                    </a>
                @else
                    <h2 class="font-semibold">Members</h2>
                @endif
                <ul class="mt-4 space-y-3">
                    @foreach ($members as $member)
                        <li class="flex items-center gap-3">
                            <span class="flex size-9 shrink-0 items-center justify-center rounded-full bg-brand-100 text-sm font-semibold text-brand-700 dark:bg-brand-500/20 dark:text-brand-200">{{ Str::upper(Str::substr($member->name, 0, 1)) }}</span>
                            <span class="min-w-0 flex-1 truncate text-sm font-medium">{{ $member->name }}</span>
                            <x-badge :tone="['admin' => 'brand', 'lead' => 'success', 'member' => 'neutral'][$member->pivot->role->value]">{{ Str::ucfirst($member->pivot->role->value) }}</x-badge>
                        </li>
                    @endforeach
                </ul>
            </x-card>

            @if ($canManage)
                <x-resource-card :href="route('departments.access-code.index', $department)" icon="key" title="Access code">
                    View and generate a new one
                </x-resource-card>

                <x-resource-card :href="route('departments.invitations.index', $department)" icon="mail" title="Invitations">
                    {{ $invitationsCount }} pending
                </x-resource-card>
            @endif

            <x-button :href="route('departments.membership.edit', $department)" variant="danger" class="w-full">Leave department</x-button>
        </div>
    </div>

    @php $currentFolderName = $path === '' ? $department->name : basename($path); @endphp

    <x-modal id="upload-files-dialog" title="Upload to {{ $currentFolderName }}" icon="cloud">
        <x-upload-dropzone :department="$department" :path="$path" />
    </x-modal>

    @if ($canManage)
        <x-modal id="rename-department-dialog" title="Rename {{ $department->name }}" icon="pencil" :open="$errors->renameDepartment->isNotEmpty()"
            description="Only its name changes. Its ID ({{ $department->slug }}) stays the same, so nothing that already points to it breaks.">
            <form method="POST" action="{{ route('departments.update', $department) }}" class="space-y-5">
                @csrf
                @method('PUT')
                <x-field name="name" label="Department name" :value="$department->name" :error-bag="'renameDepartment'" required autofocus />
                <x-button class="w-full">Save</x-button>
            </form>
        </x-modal>
    @endif
</x-app-layout>
