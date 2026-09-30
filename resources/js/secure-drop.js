// Wires up the Secure Drop pages (resources/views/secure-drop/) to secure-drop-crypto.js, which
// does the actual encryption. Nothing here ever sends a passphrase or a private key anywhere;
// they only ever exist in memory, for as long as the one operation that needs them takes.

import * as sdc from './secure-drop-crypto.js';

function csrfToken() {
    return document.querySelector('meta[name="csrf-token"]')?.content ?? '';
}

function currentUserId() {
    return document.querySelector('meta[name="user-id"]')?.content ?? '';
}

async function postJson(url, body) {
    const response = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Accept: 'application/json', 'X-CSRF-TOKEN': csrfToken() },
        credentials: 'same-origin',
        body: JSON.stringify(body),
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
        const message = data.errors ? Object.values(data.errors)[0][0] : (data.message ?? 'Something went wrong.');
        throw new Error(message);
    }
    return data;
}

// ── setting up a key pair ────────────────────────────────────────────────────
//
// Two steps, so the key pair itself (step 1, entirely client-side: generate or import) is a
// separate, visible action from protecting it with a passphrase (step 2). Nothing is sent to the
// server until step 2's encrypted result is ready.

const keyPairStep = document.querySelector('[data-key-step="pair"]');
const passphraseStep = document.querySelector('[data-key-step="passphrase"]');

if (keyPairStep && passphraseStep) {
    // The key pair itself, held in memory only from the moment step 1 finishes until step 2's
    // upload completes. It is never written anywhere unencrypted.
    let keyPair = null;

    document.querySelectorAll('[data-key-tab]').forEach((tab) => {
        tab.addEventListener('click', () => {
            document.querySelectorAll('[data-key-tab]').forEach((other) => {
                const selected = other === tab;
                other.setAttribute('aria-selected', String(selected));
                other.classList.toggle('bg-white', selected);
                other.classList.toggle('dark:bg-slate-900', selected);
                other.classList.toggle('shadow-sm', selected);
                other.classList.toggle('text-slate-500', !selected);
                other.classList.toggle('dark:text-slate-400', !selected);
            });
            document.querySelectorAll('[data-key-panel]').forEach((panel) => {
                panel.classList.toggle('hidden', panel.dataset.keyPanel !== tab.dataset.keyTab);
            });
            const label = document.querySelector('[data-key-pair-label]');
            label.textContent = tab.dataset.keyTab === 'import' ? 'Use these keys' : 'Generate my key pair';
        });
    });
    // The "Generate a new key" tab starts selected; give it the same active styling.
    document.querySelector('[data-key-tab="generate"]')?.classList.add('bg-white', 'dark:bg-slate-900', 'shadow-sm');

    const pairErrorBox = document.querySelector('[data-key-pair-error]');
    const pairIdle = document.querySelector('[data-key-pair-idle]');
    const pairBusy = document.querySelector('[data-key-pair-busy]');

    document.querySelector('[data-key-pair-submit]').addEventListener('click', async () => {
        pairErrorBox.classList.add('hidden');
        const importing = document.querySelector('[data-key-tab="import"]').getAttribute('aria-selected') === 'true';

        try {
            pairIdle.classList.add('hidden');
            pairBusy.classList.remove('hidden');

            if (importing) {
                const publicPem = document.getElementById('own_public_key').value.trim();
                const privatePem = document.getElementById('own_private_key').value.trim();
                if (!publicPem || !privatePem) {
                    throw new Error('Paste both your public and your private key.');
                }
                keyPair = { publicKey: await sdc.importPublicKeyPem(publicPem), privateKey: await sdc.importPrivateKeyPem(privatePem) };
            } else {
                keyPair = await sdc.generateKeyPair();
            }

            const fingerprint = await sdc.fingerprintOfPublicKey(keyPair.publicKey);
            document.querySelector('[data-key-pair-fingerprint]').textContent = `SHA-256: ${fingerprint}`;
            keyPairStep.classList.add('hidden');
            passphraseStep.classList.remove('hidden');
            document.getElementById('passphrase')?.focus();
        } catch (error) {
            keyPair = null;
            pairErrorBox.textContent = error.message || (importing ? 'Those do not look like a valid key pair.' : 'Could not generate a key pair.');
            pairErrorBox.classList.remove('hidden');
        } finally {
            pairIdle.classList.remove('hidden');
            pairBusy.classList.add('hidden');
        }
    });

    document.querySelector('[data-key-pair-restart]').addEventListener('click', () => {
        keyPair = null;
        passphraseStep.classList.add('hidden');
        keyPairStep.classList.remove('hidden');
    });

    const keySetupForm = document.querySelector('[data-key-setup-form]');
    keySetupForm.addEventListener('submit', async (event) => {
        event.preventDefault();

        const errorBox = document.querySelector('[data-key-setup-error]');
        const idle = document.querySelector('[data-key-setup-idle]');
        const busy = document.querySelector('[data-key-setup-busy]');
        errorBox.classList.add('hidden');

        const passphrase = keySetupForm.passphrase.value;
        const confirmation = keySetupForm.passphrase_confirmation.value;

        try {
            if (!keyPair) {
                throw new Error('Set up your key pair first.');
            }
            if (passphrase.length < 8) {
                throw new Error('Choose a passphrase of at least 8 characters.');
            }
            if (passphrase !== confirmation) {
                throw new Error('The passphrases do not match.');
            }

            idle.classList.add('hidden');
            busy.classList.remove('hidden');

            const { publicKey, privateKey } = keyPair;
            const publicKeyPem = await sdc.exportPublicKeyPem(publicKey);
            const challenge = await postJson('/secure-drop/keys/challenge', { public_key: publicKeyPem });
            const proof = await sdc.decryptChallenge(privateKey, challenge.data.ciphertext);
            const encryptedPrivateKey = await sdc.encryptPrivateKey(privateKey, passphrase);

            // A local backup, saved to this device before the server ever hears about it: if the
            // upload below fails, or the account is later lost, this file alone (plus the
            // passphrase) is still enough to recover the key.
            downloadTextFile('ctos-secure-drop-key.pem', encryptedPrivateKey);

            await postJson('/secure-drop/keys', { proof, encrypted_private_key: encryptedPrivateKey });

            window.location.reload();
        } catch (error) {
            errorBox.textContent = error.message || 'Could not set up your key. Check the fields and try again.';
            errorBox.classList.remove('hidden');
            idle.classList.remove('hidden');
            busy.classList.add('hidden');
        }
    });
}

/** Saves a small text file to this device's usual downloads location, the same way a normal link download would. */
function downloadTextFile(filename, text) {
    const url = URL.createObjectURL(new Blob([text], { type: 'application/x-pem-file' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = filename;
    link.click();
    URL.revokeObjectURL(url);
}

// ── the Sent / Received tabs ───────────────────────────────────────────────

document.querySelectorAll('[data-drop-tab]').forEach((tab) => {
    tab.addEventListener('click', () => {
        document.querySelectorAll('[data-drop-tab]').forEach((other) => {
            const selected = other === tab;
            other.setAttribute('aria-selected', String(selected));
            other.classList.toggle('bg-white', selected);
            other.classList.toggle('dark:bg-slate-900', selected);
            other.classList.toggle('shadow-sm', selected);
            other.classList.toggle('text-slate-500', !selected);
            other.classList.toggle('dark:text-slate-400', !selected);
        });
        document.querySelectorAll('[data-drop-panel]').forEach((panel) => {
            panel.classList.toggle('hidden', panel.dataset.dropPanel !== tab.dataset.dropTab);
        });
    });
});
document.querySelector('[data-drop-tab="sent"]')?.classList.add('bg-white', 'dark:bg-slate-900', 'shadow-sm');

// ── opening a received (or sent) drop ────────────────────────────────────────

const openDialog = document.getElementById('open-drop-dialog');

if (openDialog) {
    let openingDropId = null;

    document.querySelectorAll('[data-open-drop]').forEach((button) => {
        button.addEventListener('click', () => {
            openingDropId = button.dataset.openDrop;
            openDialog.querySelector('[data-open-drop-name]').textContent = `Open "${button.closest('div').querySelector('.font-medium')?.textContent ?? ''}"`;
            openDialog.querySelector('[data-open-drop-error]').classList.add('hidden');
            openDialog.querySelector('#open-drop-passphrase').value = '';
            openDialog.showModal();
        });
    });

    openDialog.querySelector('[data-open-drop-submit]').addEventListener('click', async () => {
        const errorBox = openDialog.querySelector('[data-open-drop-error]');
        const idle = openDialog.querySelector('[data-open-drop-idle]');
        const busy = openDialog.querySelector('[data-open-drop-busy]');
        const passphrase = openDialog.querySelector('#open-drop-passphrase').value;
        errorBox.classList.add('hidden');

        try {
            idle.classList.add('hidden');
            busy.classList.remove('hidden');

            const meta = await (await fetch(`/secure-drop/${openingDropId}`, { headers: { Accept: 'application/json' } })).json();
            const { name, iv, encrypted_key: encryptedKey } = meta.data;

            const encryptedKeyPemResponse = await fetch('/secure-drop/keys/download');
            if (!encryptedKeyPemResponse.ok) {
                throw new Error('Could not load your encrypted key.');
            }
            const privateKey = await sdc.decryptPrivateKey(await encryptedKeyPemResponse.text(), passphrase);
            const rawAesKey = await sdc.unwrapAesKey(privateKey, encryptedKey);

            const ciphertext = await (await fetch(`/secure-drop/${openingDropId}/download`)).arrayBuffer();
            const plain = await sdc.decryptFile(ciphertext, iv, rawAesKey);

            const url = URL.createObjectURL(new Blob([plain]));
            const link = document.createElement('a');
            link.href = url;
            link.download = name;
            link.click();
            URL.revokeObjectURL(url);

            openDialog.close();
        } catch (error) {
            errorBox.textContent = error.message === 'Wrong passphrase.' ? error.message : 'Could not open this file. Check your passphrase and try again.';
            errorBox.classList.remove('hidden');
        } finally {
            idle.classList.remove('hidden');
            busy.classList.add('hidden');
        }
    });
}

// ── sending a drop ────────────────────────────────────────────────────────────

const sendForm = document.querySelector('[data-send-drop-form]');

if (sendForm) {
    let selectedFiles = [];

    const filesInput = sendForm.querySelector('[data-send-drop-files]');
    const folderInput = sendForm.querySelector('[data-send-drop-files-folder]');
    const selectionLabel = sendForm.querySelector('[data-send-drop-selection]');

    function filesChosen(fileList) {
        selectedFiles = Array.from(fileList);
        selectionLabel.textContent = `${selectedFiles.length} ${selectedFiles.length === 1 ? 'file' : 'files'} selected`;
        selectionLabel.classList.remove('hidden');
    }

    sendForm.querySelector('[data-send-drop-choose-files]').addEventListener('click', () => filesInput.click());
    sendForm.querySelector('[data-send-drop-choose-folder]').addEventListener('click', () => folderInput.click());
    filesInput.addEventListener('change', () => filesChosen(filesInput.files));
    folderInput.addEventListener('change', () => filesChosen(folderInput.files));

    sendForm.addEventListener('submit', async (event) => {
        event.preventDefault();

        const errorBox = sendForm.querySelector('[data-send-drop-error]');
        const progress = sendForm.querySelector('[data-send-drop-progress]');
        const idle = sendForm.querySelector('[data-send-drop-idle]');
        const busy = sendForm.querySelector('[data-send-drop-busy]');
        const submit = sendForm.querySelector('[data-send-drop-submit]');
        errorBox.classList.add('hidden');

        try {
            if (selectedFiles.length === 0) {
                throw new Error('Choose at least one file.');
            }

            const recipients = Array.from(sendForm.querySelectorAll('[data-recipient-id]'))
                .filter((checkbox) => checkbox.checked)
                .map((checkbox) => ({ id: checkbox.dataset.recipientId, publicKeyPem: checkbox.dataset.recipientPublicKey }));
            if (recipients.length === 0) {
                throw new Error('Pick at least one person to send it to.');
            }

            idle.classList.add('hidden');
            busy.classList.remove('hidden');
            submit.disabled = true;

            const recipientPublicKeys = await Promise.all(recipients.map((r) => sdc.importPublicKeyPem(r.publicKeyPem)));

            for (let i = 0; i < selectedFiles.length; i++) {
                const file = selectedFiles[i];
                progress.textContent = `Encrypting ${i + 1} of ${selectedFiles.length}: ${file.name}`;
                progress.classList.remove('hidden');

                const { ciphertext, iv, rawKey } = await sdc.encryptFile(await file.arrayBuffer());
                const wrappedKeys = await Promise.all(recipientPublicKeys.map((key) => sdc.wrapAesKeyFor(key, rawKey)));

                const body = new FormData();
                body.append('_token', csrfToken());
                body.append('name', file.webkitRelativePath || file.name);
                body.append('iv', iv);
                body.append('ciphertext', new Blob([ciphertext]), 'ciphertext');
                recipients.forEach((recipient, index) => {
                    body.append(`recipients[${index}][user_id]`, recipient.id);
                    body.append(`recipients[${index}][encrypted_key]`, wrappedKeys[index]);
                });

                const response = await fetch(sendForm.dataset.recipientsUrl, { method: 'POST', headers: { Accept: 'application/json' }, body });
                if (!response.ok) {
                    const data = await response.json().catch(() => ({}));
                    throw new Error(data.errors ? Object.values(data.errors)[0][0] : `Could not send "${file.name}".`);
                }
            }

            window.location.href = '/secure-drop';
        } catch (error) {
            errorBox.textContent = error.message || 'Could not send this. Check the fields and try again.';
            errorBox.classList.remove('hidden');
            idle.classList.remove('hidden');
            busy.classList.add('hidden');
            submit.disabled = false;
            progress.classList.add('hidden');
        }
    });
}

// ── sending directly by email, not scoped to a department ─────────────────────
//
// Whoever the address belongs to decides how: an account with its own Secure Drop key gets the
// usual end-to-end treatment; anyone else gets a one-time password instead, emailed to them along
// with an invitation to set up their own key. Generating and using that password is the one
// place in this whole feature the server sees a plaintext secret — it has to, to email it.

const sendDropDialog = document.getElementById('send-drop-dialog');

if (sendDropDialog) {
    sendDropDialog.querySelectorAll('[data-send-tab]').forEach((tab) => {
        tab.addEventListener('click', () => {
            sendDropDialog.querySelectorAll('[data-send-tab]').forEach((other) => {
                const selected = other === tab;
                other.setAttribute('aria-selected', String(selected));
                other.classList.toggle('bg-white', selected);
                other.classList.toggle('dark:bg-slate-900', selected);
                other.classList.toggle('shadow-sm', selected);
                other.classList.toggle('text-slate-500', !selected);
                other.classList.toggle('dark:text-slate-400', !selected);
            });
            sendDropDialog.querySelectorAll('[data-send-panel]').forEach((panel) => {
                panel.classList.toggle('hidden', panel.dataset.sendPanel !== tab.dataset.sendTab);
            });
        });
    });
    sendDropDialog.querySelector('[data-send-tab="department"]')?.classList.add('bg-white', 'dark:bg-slate-900', 'shadow-sm');

    const emailForm = sendDropDialog.querySelector('[data-send-email-form]');
    let selectedFile = null;

    const fileInput = emailForm.querySelector('[data-send-email-file]');
    const fileLabel = emailForm.querySelector('[data-send-email-file-label]');
    emailForm.querySelector('[data-send-email-choose-file]').addEventListener('click', () => fileInput.click());
    fileInput.addEventListener('change', () => {
        selectedFile = fileInput.files[0] ?? null;
        fileLabel.textContent = selectedFile ? selectedFile.name : 'Choose a file';
    });

    emailForm.addEventListener('submit', async (event) => {
        event.preventDefault();

        const errorBox = emailForm.querySelector('[data-send-email-error]');
        const progress = emailForm.querySelector('[data-send-email-progress]');
        const idle = emailForm.querySelector('[data-send-email-idle]');
        const busy = emailForm.querySelector('[data-send-email-busy]');
        const submit = emailForm.querySelector('[data-send-email-submit]');
        const email = emailForm.querySelector('#send-email-address').value.trim();
        errorBox.classList.add('hidden');

        try {
            if (!email) {
                throw new Error('Enter an email address.');
            }
            if (!selectedFile) {
                throw new Error('Choose a file.');
            }

            idle.classList.add('hidden');
            busy.classList.remove('hidden');
            submit.disabled = true;
            progress.textContent = 'Looking up that email…';
            progress.classList.remove('hidden');

            const lookup = await postJson('/secure-drop/lookup', { email });

            progress.textContent = `Encrypting ${selectedFile.name}…`;
            const { ciphertext, iv, rawKey } = await sdc.encryptFile(await selectedFile.arrayBuffer());

            const ownPublicKey = await sdc.importPublicKeyPem(sendDropDialog.dataset.ownPublicKey);
            const ownWrappedKey = await sdc.wrapAesKeyFor(ownPublicKey, rawKey);

            const body = new FormData();
            body.append('_token', csrfToken());
            body.append('name', selectedFile.name);
            body.append('iv', iv);
            body.append('ciphertext', new Blob([ciphertext]), 'ciphertext');
            body.append('recipients[0][user_id]', currentUserId());
            body.append('recipients[0][encrypted_key]', ownWrappedKey);

            if (lookup.data.has_account) {
                const theirPublicKey = await sdc.importPublicKeyPem(lookup.data.public_key);
                const theirWrappedKey = await sdc.wrapAesKeyFor(theirPublicKey, rawKey);
                body.append('recipients[1][user_id]', lookup.data.id);
                body.append('recipients[1][encrypted_key]', theirWrappedKey);
            } else {
                const password = sdc.generatePassword();
                const passwordWrappedKey = await sdc.wrapAesKeyWithPassword(rawKey, password);
                body.append('password_recipient[email]', email);
                body.append('password_recipient[encrypted_key]', passwordWrappedKey);
                body.append('password', password);
            }

            progress.textContent = `Sending ${selectedFile.name}…`;
            const response = await fetch('/secure-drop/send', { method: 'POST', headers: { Accept: 'application/json' }, body });
            if (!response.ok) {
                const data = await response.json().catch(() => ({}));
                throw new Error(data.errors ? Object.values(data.errors)[0][0] : `Could not send "${selectedFile.name}".`);
            }

            window.location.href = '/secure-drop';
        } catch (error) {
            errorBox.textContent = error.message || 'Could not send this. Check the fields and try again.';
            errorBox.classList.remove('hidden');
            idle.classList.remove('hidden');
            busy.classList.add('hidden');
            submit.disabled = false;
            progress.classList.add('hidden');
        }
    });
}

// ── opening one sent to someone with no account, given the link from their email ──

const sharedDrop = document.querySelector('[data-shared-drop]');

if (sharedDrop) {
    const errorBox = sharedDrop.querySelector('[data-shared-drop-error]');
    const idle = sharedDrop.querySelector('[data-shared-drop-idle]');
    const busy = sharedDrop.querySelector('[data-shared-drop-busy]');
    const token = sharedDrop.dataset.sharedToken;

    sharedDrop.querySelector('[data-shared-drop-submit]').addEventListener('click', async () => {
        const password = sharedDrop.querySelector('#shared-drop-password').value;
        errorBox.classList.add('hidden');

        try {
            idle.classList.add('hidden');
            busy.classList.remove('hidden');

            const meta = await (await fetch(`/secure-drop/shared/${token}/meta`, { headers: { Accept: 'application/json' } })).json();
            const { name, iv, encrypted_key: encryptedKey } = meta.data;

            const rawAesKey = await sdc.unwrapAesKeyWithPassword(encryptedKey, password);

            const ciphertext = await (await fetch(`/secure-drop/shared/${token}/download`)).arrayBuffer();
            const plain = await sdc.decryptFile(ciphertext, iv, rawAesKey);

            const url = URL.createObjectURL(new Blob([plain]));
            const link = document.createElement('a');
            link.href = url;
            link.download = name;
            link.click();
            URL.revokeObjectURL(url);
        } catch (error) {
            errorBox.textContent = error.message === 'Wrong password.' ? error.message : 'Could not open this file. Check the password and try again.';
            errorBox.classList.remove('hidden');
        } finally {
            idle.classList.remove('hidden');
            busy.classList.add('hidden');
        }
    });
}
