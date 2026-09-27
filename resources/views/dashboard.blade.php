<x-app-layout title="Dashboard">
    <x-page-header :title="'Welcome back, '.Str::before(auth()->user()->name, ' ')" subtitle="Here's where things stand." />

    <div class="grid gap-4 sm:grid-cols-2">
        <x-resource-card :href="route('organizations.index')" icon="building" title="Organizations">
            {{ $organizationsCount }} {{ Str::plural('organization', $organizationsCount) }}
        </x-resource-card>
        <x-resource-card :href="route('departments.index')" icon="folder" title="Departments">
            {{ $departmentsCount }} {{ Str::plural('department', $departmentsCount) }} joined
        </x-resource-card>
    </div>
</x-app-layout>
