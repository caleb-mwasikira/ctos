@props(['name', 'label', 'hint' => null, 'value' => null, 'errorBag' => 'default'])

<div>
    <label for="{{ $name }}" class="block text-sm font-medium text-slate-700 dark:text-slate-300">{{ $label }}</label>
    <textarea
        id="{{ $name }}"
        name="{{ $name }}"
        @error($name, $errorBag) aria-invalid="true" @enderror
        {{ $attributes->merge(['class' => 'mt-1.5 block w-full rounded-lg border-0 bg-white px-3.5 py-2.5 font-mono text-xs text-slate-900 shadow-sm ring-1 ring-inset ring-slate-300 placeholder:font-sans placeholder:text-sm placeholder:text-slate-400 focus:ring-2 focus:ring-inset focus:ring-brand-500 aria-invalid:ring-red-400 dark:bg-slate-900 dark:text-white dark:ring-slate-700 dark:placeholder:text-slate-500']) }}
    >{{ old($name, $value) }}</textarea>
    @if ($hint)
        <p class="mt-1.5 text-xs text-slate-500 dark:text-slate-400">{{ $hint }}</p>
    @endif
    @error($name, $errorBag)
        <p class="mt-1.5 text-sm text-red-600 dark:text-red-400">{{ $message }}</p>
    @enderror
</div>
