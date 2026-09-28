// Any button with data-copy="text" copies that text and briefly shows "Copied".
document.addEventListener('click', async (event) => {
    const button = event.target.closest('[data-copy]');

    if (!button) {
        return;
    }

    try {
        await navigator.clipboard.writeText(button.dataset.copy);
    } catch {
        return;
    }

    const label = button.querySelector('[data-copy-label]');
    const original = label?.textContent;

    if (label) {
        label.textContent = 'Copied';
        setTimeout(() => (label.textContent = original), 1500);
    }
});

// A button with data-dialog-open="id" opens that <dialog>. Clicking the dimmed area around an open
// dialog closes it, but only when the press started there too, so dragging to select text
// inside the dialog and letting go outside does not close it.
let pressStartedOutsideDialog = false;

function isOutside(dialog, event) {
    const box = dialog.getBoundingClientRect();

    return event.clientX < box.left || event.clientX > box.right || event.clientY < box.top || event.clientY > box.bottom;
}

document.addEventListener('pointerdown', (event) => {
    pressStartedOutsideDialog = event.target instanceof HTMLDialogElement && isOutside(event.target, event);
});

document.addEventListener('click', (event) => {
    const opener = event.target.closest('[data-dialog-open]');

    if (opener) {
        document.getElementById(opener.dataset.dialogOpen)?.showModal();

        return;
    }

    if (event.target instanceof HTMLDialogElement && pressStartedOutsideDialog && isOutside(event.target, event)) {
        event.target.close();
    }

    pressStartedOutsideDialog = false;
});

// A <details data-menu> is a dropdown menu: it closes when you click anywhere else, or press Escape.
document.addEventListener('click', (event) => {
    document.querySelectorAll('details[data-menu][open]').forEach((menu) => {
        if (!menu.contains(event.target)) {
            menu.open = false;
        }
    });
});

document.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
        document.querySelectorAll('details[data-menu][open]').forEach((menu) => (menu.open = false));
    }
});

// A <button data-password-toggle> next to a password field shows or hides what is typed in it.
document.addEventListener('click', (event) => {
    const toggle = event.target.closest('[data-password-toggle]');

    if (!toggle) {
        return;
    }

    const input = toggle.parentElement.querySelector('input');
    const reveal = input.type === 'password';

    input.type = reveal ? 'text' : 'password';
    toggle.setAttribute('aria-pressed', String(reveal));
    toggle.setAttribute('aria-label', reveal ? 'Hide password' : 'Show password');
    toggle.querySelector('[data-icon-show]').classList.toggle('hidden', reveal);
    toggle.querySelector('[data-icon-hide]').classList.toggle('hidden', !reveal);
});

// A <dialog data-open> starts open, so a form that failed validation is shown again with its errors.
document.querySelectorAll('dialog[data-open]').forEach((dialog) => dialog.showModal());

// The file explorer on a department page (resources/views/departments/partials/explorer.blade.php)
// polls its own markup every few seconds and swaps it in, so a file someone else adds, changes or
// removes from another device or the desktop/Android app shows up without a reload.
document.querySelectorAll('[data-explorer]').forEach((section) => {
    const url = section.dataset.explorerUrl;
    let current = section.innerHTML;

    async function poll() {
        // No point asking while the tab isn't visible; pick back up next tick once it is.
        if (document.hidden) {
            return;
        }

        let html;
        try {
            const response = await fetch(url, { headers: { Accept: 'text/html', 'X-Explorer-Poll': '1' } });

            if (!response.ok) {
                return;
            }

            html = await response.text();
        } catch {
            // Offline, or the page is navigating away; just try again next tick.
            return;
        }

        if (html !== current) {
            current = html;
            section.innerHTML = html;
        }
    }

    setInterval(poll, 4000);
});

// The drag-and-drop upload box (resources/views/components/upload-dropzone.blade.php): dropping
// or choosing files or a whole folder fills its file inputs, then "submit" below builds the
// "paths" field (each file's path relative to what was dropped) before the form actually posts.

// Files added by drag and drop, since their relative path (unlike a folder chosen by hand, which
// the browser puts in file.webkitRelativePath) has nowhere else to live.
const droppedRelativePaths = new WeakMap();

// How many un-matched "dragenter"s a drop zone has seen: dragenter/dragleave fire on every child
// element as the pointer crosses it, not just once for the zone, so a plain enter/leave pair would
// flicker the highlight constantly. Only leaving from a count of zero means truly leaving the zone.
const dragCounters = new WeakMap();

function readDirectoryEntries(reader) {
    return new Promise((resolve, reject) => reader.readEntries(resolve, reject));
}

async function collectFromEntry(entry, prefix, into) {
    if (entry.isFile) {
        const file = await new Promise((resolve, reject) => entry.file(resolve, reject));
        into.push({ file, relativePath: prefix + entry.name });

        return;
    }

    if (!entry.isDirectory) {
        return;
    }

    const reader = entry.createReader();
    let batch;
    do {
        // A directory reader hands out entries in batches; an empty batch means there are no more.
        batch = await readDirectoryEntries(reader);
        for (const child of batch) {
            await collectFromEntry(child, `${prefix}${entry.name}/`, into);
        }
    } while (batch.length > 0);
}

// Every file dropped, each with its path relative to whatever was dropped (a lone file's is just
// its name). Falls back to the flat file list on a browser with no directory-entry support, which
// loses any folder structure but still uploads the files themselves.
async function collectDroppedFiles(dataTransfer) {
    const items = Array.from(dataTransfer.items || []).filter((item) => item.kind === 'file');

    if (items.length === 0 || typeof items[0].webkitGetAsEntry !== 'function') {
        return Array.from(dataTransfer.files).map((file) => ({ file, relativePath: file.name }));
    }

    const collected = [];
    for (const item of items) {
        const entry = item.webkitGetAsEntry();

        if (entry) {
            await collectFromEntry(entry, '', collected);
        }
    }

    return collected;
}

function uploadSelectionChanged(zone, count) {
    const label = zone.querySelector('[data-upload-selection]');
    const submit = zone.querySelector('[data-upload-submit]');

    if (label) {
        label.textContent = count > 0 ? `${count} ${count === 1 ? 'item' : 'items'} ready to upload` : '';
        label.classList.toggle('hidden', count === 0);
    }

    submit?.classList.toggle('hidden', count === 0);
}

document.addEventListener('dragenter', (event) => {
    const zone = event.target.closest('[data-upload-dropzone]');

    if (!zone) {
        return;
    }

    event.preventDefault();
    dragCounters.set(zone, (dragCounters.get(zone) || 0) + 1);
    zone.setAttribute('data-dragover', '');
});

document.addEventListener('dragover', (event) => {
    // A continuous "preventDefault" while hovering is what tells the browser a drop is accepted here.
    if (event.target.closest('[data-upload-dropzone]')) {
        event.preventDefault();
    }
});

document.addEventListener('dragleave', (event) => {
    const zone = event.target.closest('[data-upload-dropzone]');

    if (!zone) {
        return;
    }

    const remaining = (dragCounters.get(zone) || 1) - 1;
    dragCounters.set(zone, remaining);

    if (remaining <= 0) {
        dragCounters.delete(zone);
        zone.removeAttribute('data-dragover');
    }
});

document.addEventListener('drop', async (event) => {
    const zone = event.target.closest('[data-upload-dropzone]');

    if (!zone) {
        return;
    }

    event.preventDefault();
    dragCounters.delete(zone);
    zone.removeAttribute('data-dragover');

    const input = zone.closest('[data-upload-form]')?.querySelector('[data-upload-input-dropped]');

    if (!input) {
        return;
    }

    const dropped = await collectDroppedFiles(event.dataTransfer);

    if (dropped.length === 0) {
        return;
    }

    const transfer = new DataTransfer();
    dropped.forEach(({ file, relativePath }) => {
        transfer.items.add(file);
        droppedRelativePaths.set(file, relativePath);
    });
    input.files = transfer.files;

    uploadSelectionChanged(zone, dropped.length);
});

// "Choose files" / "Choose a folder" just click the matching hidden <input type="file">.
document.addEventListener('click', (event) => {
    const chooser = event.target.closest('[data-upload-choose]');

    if (!chooser) {
        return;
    }

    const selector = chooser.dataset.uploadChoose === 'folder' ? '[data-upload-input-folder]' : '[data-upload-input-files]';
    chooser.closest('[data-upload-form]')?.querySelector(selector)?.click();
});

document.addEventListener('change', (event) => {
    const input = event.target.closest('[data-upload-input-files], [data-upload-input-folder]');
    const zone = input?.closest('[data-upload-form]')?.querySelector('[data-upload-dropzone]');

    if (zone) {
        uploadSelectionChanged(zone, input.files.length);
    }
});

// Right before the form posts, gather every file across all three inputs (whichever was used)
// into "paths", in the same order PHP will see them in $request->file('files').
document.addEventListener('submit', (event) => {
    const form = event.target.closest('[data-upload-form]');

    if (!form) {
        return;
    }

    const paths = [];
    let fileCount = 0;

    form.querySelectorAll('input[type="file"]').forEach((input) => {
        Array.from(input.files || []).forEach((file) => {
            fileCount++;
            paths.push(droppedRelativePaths.get(file) || file.webkitRelativePath || file.name);
        });
    });

    if (fileCount === 0) {
        event.preventDefault();

        return;
    }

    form.querySelector('[data-upload-paths]').value = JSON.stringify(paths);
});
