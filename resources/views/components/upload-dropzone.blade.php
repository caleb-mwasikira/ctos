@props(['department', 'path' => ''])

{{--
    Drag files (or a whole folder) here to upload them, or choose them by hand. The JS
    (resources/js/app.js, the "data-upload-*" handlers) builds the "paths" field before the form
    is submitted: for a folder, it is each file's path relative to the folder that was dropped or
    chosen, so DepartmentFileController::upload can put everything back where it was.
--}}
<form
    method="POST"
    action="{{ route('departments.files.upload', $department) }}"
    enctype="multipart/form-data"
    data-upload-form
    {{ $attributes->merge(['class' => '']) }}
>
    @csrf
    <input type="hidden" name="path" value="{{ $path }}">
    <input type="hidden" name="paths" data-upload-paths value="[]">
    <input type="file" name="files[]" multiple class="sr-only" data-upload-input-files aria-hidden="true" tabindex="-1">
    <input type="file" name="files[]" webkitdirectory class="sr-only" data-upload-input-folder aria-hidden="true" tabindex="-1">
    <input type="file" name="files[]" multiple class="sr-only" data-upload-input-dropped aria-hidden="true" tabindex="-1">

    <div
        data-upload-dropzone
        class="flex flex-col items-center rounded-2xl border-2 border-dashed border-slate-300 bg-slate-50/60 px-6 py-10 text-center transition data-[dragover]:border-brand-500 data-[dragover]:bg-brand-50 dark:border-slate-700 dark:bg-slate-800/40 dark:data-[dragover]:border-brand-500 dark:data-[dragover]:bg-brand-500/10"
    >
        <span class="flex size-12 items-center justify-center rounded-2xl bg-white text-brand-500 shadow-sm ring-1 ring-slate-200 dark:bg-slate-900 dark:ring-slate-700">
            <x-icon name="cloud" class="size-6" />
        </span>
        <p class="mt-4 text-sm font-medium">Drag files or a folder here to upload them</p>
        <div class="mt-3 flex flex-wrap items-center justify-center gap-x-2 gap-y-1 text-sm">
            <button type="button" data-upload-choose="files" class="rounded-lg px-3 py-1.5 font-medium text-brand-600 hover:bg-brand-50 dark:text-brand-300 dark:hover:bg-brand-500/10">Choose files</button>
            <span class="text-slate-300 dark:text-slate-600" aria-hidden="true">&middot;</span>
            <button type="button" data-upload-choose="folder" class="rounded-lg px-3 py-1.5 font-medium text-brand-600 hover:bg-brand-50 dark:text-brand-300 dark:hover:bg-brand-500/10">Choose a folder</button>
        </div>
        <p data-upload-selection class="mt-3 hidden text-sm text-slate-600 dark:text-slate-400"></p>
        <x-button type="submit" size="sm" class="mt-4 hidden" data-upload-submit><x-icon name="cloud" class="size-4" /> Upload</x-button>
    </div>
</form>
