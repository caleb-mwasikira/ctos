@php
    use App\Enums\DepartmentRole;

    $organization = $department->organization;
    $roleTones = ['admin' => 'brand', 'lead' => 'success', 'member' => 'neutral'];
    $lockedCount = $members->filter(fn ($member) => $member->pivot->locked_at || in_array($member->id, $lockedOutOfOrganization))->count();
@endphp

<x-app-layout :title="$department->name.' members'">
    <x-page-header
        title="Members"
        :crumbs="['Dashboard' => route('dashboard'), $organization->name => route('organizations.show', $organization), $department->name => route('departments.show', $department)]"
    >
        <x-slot:subtitle>
            <span class="text-sm">
                {{ $members->count() }} {{ Str::plural('member', $members->count()) }} of {{ $department->name }}
                @if ($lockedCount > 0)
                    &middot; {{ $lockedCount }} locked
                @endif
            </span>
        </x-slot:subtitle>
    </x-page-header>

    @unless ($canManageMembers)
        <p class="mb-6 rounded-xl bg-slate-100 px-4 py-3 text-sm text-slate-600 dark:bg-slate-800 dark:text-slate-300">
            Only the owner of {{ $organization->name }} can assign leads or lock members.
        </p>
    @endunless

    <x-card class="divide-y divide-slate-100 p-0 dark:divide-slate-800">
        @foreach ($members as $member)
            @php
                $role = $member->pivot->role;
                $lockedHere = $member->pivot->locked_at !== null;
                $lockedOut = in_array($member->id, $lockedOutOfOrganization);
                $isOwner = $member->id === $organization->owner_id;
                $changeable = $canManageMembers && ! $isOwner && $role !== DepartmentRole::Admin;
            @endphp

            <div class="flex items-center gap-4 p-5 {{ $lockedHere || $lockedOut ? 'bg-slate-50/70 dark:bg-slate-800/30' : '' }}">
                <span class="flex size-10 shrink-0 items-center justify-center rounded-full bg-brand-100 text-sm font-semibold text-brand-700 dark:bg-brand-500/20 dark:text-brand-200">{{ Str::upper(Str::substr($member->name, 0, 1)) }}</span>

                <div class="min-w-0 flex-1">
                    <p class="truncate font-semibold {{ $lockedHere || $lockedOut ? 'text-slate-500' : '' }}">{{ $member->name }}</p>
                    <p class="truncate text-sm text-slate-500">{{ $member->email }}</p>
                </div>

                <div class="flex flex-wrap items-center justify-end gap-2">
                    @if ($isOwner)
                        <x-badge tone="brand">Owner</x-badge>
                    @else
                        <x-badge :tone="$roleTones[$role->value]">{{ $role === DepartmentRole::Lead ? 'Department lead' : Str::ucfirst($role->value) }}</x-badge>
                    @endif
                    @if ($lockedHere)
                        <x-badge tone="soon">Locked in {{ $department->name }}</x-badge>
                    @endif
                    @if ($lockedOut)
                        <x-badge tone="soon">Locked out of {{ $organization->name }}</x-badge>
                    @endif
                </div>

                @if ($changeable)
                    <details class="relative" data-menu>
                        <summary class="flex size-9 cursor-pointer items-center justify-center rounded-lg text-slate-500 hover:bg-slate-100 dark:hover:bg-slate-800" aria-label="Actions for {{ $member->name }}">
                            <x-icon name="dots" class="size-5" />
                        </summary>
                        <div class="absolute right-0 top-10 z-20 w-72 rounded-2xl border border-slate-200 bg-white p-2 shadow-xl dark:border-slate-800 dark:bg-slate-900">
                            @php
                                $item = 'flex w-full items-center gap-2 rounded-lg px-3 py-2 text-left text-sm hover:bg-slate-100 dark:hover:bg-slate-800';
                            @endphp

                            <form method="POST" action="{{ route('departments.members.update', [$department, $member]) }}">
                                @csrf
                                @method('PATCH')
                                <input type="hidden" name="role" value="{{ $role === DepartmentRole::Lead ? 'member' : 'lead' }}">
                                <button type="submit" class="{{ $item }}">
                                    <x-icon name="key" class="size-4 text-slate-400" />
                                    {{ $role === DepartmentRole::Lead ? 'Remove as department lead' : 'Make department lead' }}
                                </button>
                            </form>

                            <div class="my-1 border-t border-slate-100 dark:border-slate-800"></div>

                            <form method="POST" action="{{ route('departments.members.lock.'.($lockedHere ? 'destroy' : 'store'), [$department, $member]) }}">
                                @csrf
                                @method($lockedHere ? 'DELETE' : 'PUT')
                                <button type="submit" class="{{ $item }} {{ $lockedHere ? '' : 'text-red-600 dark:text-red-400' }}">
                                    <x-icon name="lock" class="size-4" />
                                    {{ $lockedHere ? 'Unlock in '.$department->name : 'Lock out of '.$department->name }}
                                </button>
                            </form>

                            <form method="POST" action="{{ route('organizations.members.lock.'.($lockedOut ? 'destroy' : 'store'), [$organization, $member]) }}">
                                @csrf
                                @method($lockedOut ? 'DELETE' : 'PUT')
                                <button type="submit" class="{{ $item }} {{ $lockedOut ? '' : 'text-red-600 dark:text-red-400' }}">
                                    <x-icon name="building" class="size-4" />
                                    {{ $lockedOut ? 'Unlock in '.$organization->name : 'Lock out of '.$organization->name }}
                                </button>
                            </form>
                        </div>
                    </details>
                @endif
            </div>
        @endforeach
    </x-card>
</x-app-layout>
