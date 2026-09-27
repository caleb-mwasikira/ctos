@props(['name', 'label', 'type' => 'text', 'hint' => null, 'value' => null, 'errorBag' => 'default'])

<div>
    <label for="{{ $name }}" class="block text-sm font-medium text-slate-700 dark:text-slate-300">{{ $label }}</label>
    <div class="relative">
        <input
            id="{{ $name }}"
            name="{{ $name }}"
            type="{{ $type }}"
            @if ($type !== 'password') value="{{ old($name, $value) }}" @endif
            @error($name, $errorBag) aria-invalid="true" @enderror
            {{ $attributes->merge(['class' => 'mt-1.5 block w-full rounded-lg border-0 bg-white px-3.5 py-2.5 text-sm text-slate-900 shadow-sm ring-1 ring-inset ring-slate-300 placeholder:text-slate-400 focus:ring-2 focus:ring-inset focus:ring-brand-500 aria-invalid:ring-red-400 dark:bg-slate-900 dark:text-white dark:ring-slate-700 dark:placeholder:text-slate-500'.($type === 'password' ? ' pr-11' : '')]) }}
        >
        @if ($type === 'password')
            {{-- Shows or hides the password (see app.js). --}}
            <button
                type="button"
                data-password-toggle
                aria-label="Show password"
                aria-pressed="false"
                class="absolute inset-y-0 right-0 mt-1.5 flex items-center rounded-r-lg px-3.5 text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
            >
                <x-icon name="eye" data-icon-show />
                <x-icon name="eye-off" data-icon-hide class="hidden" />
            </button>
        @endif
    </div>
    @if ($hint)
        <p class="mt-1.5 text-xs text-slate-500 dark:text-slate-400">{{ $hint }}</p>
    @endif
    @error($name, $errorBag)
        <p class="mt-1.5 text-sm text-red-600 dark:text-red-400">{{ $message }}</p>
    @enderror
</div>
