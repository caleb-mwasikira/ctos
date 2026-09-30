// Secure Drop's cryptography (docs/secure-drop.md), using only the browser's built-in
// crypto.subtle — no crypto library to trust besides the one already in the browser. Every
// function here runs on the sender's or recipient's own device; nothing in this file ever sends
// a private key or a passphrase anywhere.
//
// RSA-OAEP uses SHA-1 (only for OAEP's own padding, not a signature — this is not the weakness
// SHA-1 has elsewhere): the website's PHP encrypts a key pair's very first challenge with
// openssl_public_encrypt(), whose simple OPENSSL_PKCS1_OAEP_PADDING has no way to ask for a
// different hash, so every device speaking this protocol has to agree on the one PHP has.

const PBKDF2_ITERATIONS = 210_000; // OWASP's 2023 minimum for PBKDF2-SHA256
const ENVELOPE_VERSION = 1;
const ENCRYPTED_PRIVATE_KEY_HEADER = '-----BEGIN CTOS ENCRYPTED PRIVATE KEY-----';
const ENCRYPTED_PRIVATE_KEY_FOOTER = '-----END CTOS ENCRYPTED PRIVATE KEY-----';

// ── PEM framing ──────────────────────────────────────────────────────────────

function bytesToBase64(bytes) {
    let binary = '';
    bytes.forEach((byte) => (binary += String.fromCharCode(byte)));
    return btoa(binary);
}

function base64ToBytes(base64) {
    const binary = atob(base64.replace(/[\r\n]/g, ''));
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
    }
    return bytes;
}

function wrapPem(label, bytes) {
    const base64 = bytesToBase64(bytes);
    const lines = base64.match(/.{1,64}/g) || [];
    return `-----BEGIN ${label}-----\n${lines.join('\n')}\n-----END ${label}-----`;
}

function unwrapPem(pem, label) {
    const header = `-----BEGIN ${label}-----`;
    const footer = `-----END ${label}-----`;
    const start = pem.indexOf(header);
    const end = pem.indexOf(footer);
    if (start === -1 || end === -1) {
        throw new Error(`Expected a "${header}" PEM.`);
    }
    return base64ToBytes(pem.slice(start + header.length, end));
}

// ── the RSA-OAEP key pair (encrypt/decrypt only; it never signs anything) ───

export async function generateKeyPair() {
    return crypto.subtle.generateKey(
        { name: 'RSA-OAEP', modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: 'SHA-1' },
        true,
        ['encrypt', 'decrypt'],
    );
}

export async function exportPublicKeyPem(publicKey) {
    return wrapPem('PUBLIC KEY', new Uint8Array(await crypto.subtle.exportKey('spki', publicKey)));
}

export async function importPublicKeyPem(pem) {
    return crypto.subtle.importKey('spki', unwrapPem(pem, 'PUBLIC KEY'), { name: 'RSA-OAEP', hash: 'SHA-1' }, true, ['encrypt']);
}

/** A plain, unencrypted private key PEM ("-----BEGIN PRIVATE KEY-----"): only ever kept in memory, for someone bringing their own existing key pair. */
export async function importPrivateKeyPem(pem) {
    return crypto.subtle.importKey('pkcs8', unwrapPem(pem, 'PRIVATE KEY'), { name: 'RSA-OAEP', hash: 'SHA-1' }, true, ['decrypt']);
}

/** sha256 of the public key's DER, hex — matches the server's own fingerprint of the same key. */
export async function fingerprintOfPublicKey(publicKey) {
    const der = await crypto.subtle.exportKey('spki', publicKey);
    const hash = await crypto.subtle.digest('SHA-256', der);
    return Array.from(new Uint8Array(hash)).map((b) => b.toString(16).padStart(2, '0')).join('');
}

// ── the passphrase-protected envelope (a private key, or someone's copy of a file's AES key) ──
//
// The same PBKDF2 + AES-GCM envelope wraps two different things under two different PEM labels:
// a private key itself (someone's own Secure Drop key, protected with their passphrase) and, for
// someone with no account yet, one file's AES key (protected with a one-time password instead —
// see docs/secure-drop.md's "sending by email").

// NFC-normalized first: the same passphrase can arrive as different Unicode code point sequences
// depending on where it was typed (an accented letter composed via a Linux/AWT dead key can come
// in decomposed — a base letter plus a combining mark — while a browser's own input handling
// normally composes it), and those encode to different bytes. Both this and SecureDropCrypto.kt's
// deriveAesKeyFromPassphrase() normalize, so an envelope made on one platform still opens with
// "the same" passphrase typed on the other.
async function deriveAesKeyFromPassphrase(passphrase, salt, iterations) {
    const material = await crypto.subtle.importKey('raw', new TextEncoder().encode(passphrase.normalize('NFC')), 'PBKDF2', false, ['deriveKey']);
    return crypto.subtle.deriveKey(
        { name: 'PBKDF2', salt, iterations, hash: 'SHA-256' },
        material,
        { name: 'AES-GCM', length: 256 },
        false,
        ['encrypt', 'decrypt'],
    );
}

async function encryptBytesWithPassphrase(label, plainBytes, passphrase) {
    const salt = crypto.getRandomValues(new Uint8Array(16));
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const aesKey = await deriveAesKeyFromPassphrase(passphrase, salt, PBKDF2_ITERATIONS);
    const ciphertext = new Uint8Array(await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, aesKey, plainBytes));

    const envelope = new Uint8Array(1 + salt.length + 4 + iv.length + ciphertext.length);
    let offset = 0;
    envelope[offset++] = ENVELOPE_VERSION;
    envelope.set(salt, offset);
    offset += salt.length;
    new DataView(envelope.buffer).setUint32(offset, PBKDF2_ITERATIONS, false);
    offset += 4;
    envelope.set(iv, offset);
    offset += iv.length;
    envelope.set(ciphertext, offset);

    return wrapPem(label, envelope);
}

/** Throws (a message meant to be shown as-is) when the passphrase is wrong — AES-GCM's own tag check fails first. */
async function decryptBytesWithPassphrase(label, envelopePem, passphrase, wrongPassphraseMessage) {
    const envelope = unwrapPem(envelopePem, label);
    if (envelope.length < 1 + 16 + 4 + 12 + 16 || envelope[0] !== ENVELOPE_VERSION) {
        throw new Error('That does not look like a Secure Drop encrypted key.');
    }

    let offset = 1;
    const salt = envelope.slice(offset, offset + 16);
    offset += 16;
    const iterations = new DataView(envelope.buffer, envelope.byteOffset).getUint32(offset, false);
    offset += 4;
    const iv = envelope.slice(offset, offset + 12);
    offset += 12;
    const ciphertext = envelope.slice(offset);

    const aesKey = await deriveAesKeyFromPassphrase(passphrase, salt, iterations);
    try {
        return new Uint8Array(await crypto.subtle.decrypt({ name: 'AES-GCM', iv }, aesKey, ciphertext));
    } catch {
        throw new Error(wrongPassphraseMessage);
    }
}

export async function encryptPrivateKey(privateKey, passphrase) {
    const plainDer = new Uint8Array(await crypto.subtle.exportKey('pkcs8', privateKey));
    return encryptBytesWithPassphrase('CTOS ENCRYPTED PRIVATE KEY', plainDer, passphrase);
}

export async function decryptPrivateKey(envelopePem, passphrase) {
    const plainDer = await decryptBytesWithPassphrase('CTOS ENCRYPTED PRIVATE KEY', envelopePem, passphrase, 'Wrong passphrase.');
    return crypto.subtle.importKey('pkcs8', plainDer, { name: 'RSA-OAEP', hash: 'SHA-1' }, true, ['decrypt']);
}

/** For someone with no account yet: a file's raw AES key, protected with a one-time password instead of a public key. */
export async function wrapAesKeyWithPassword(rawAesKey, password) {
    return encryptBytesWithPassphrase('CTOS PASSWORD PROTECTED KEY', new Uint8Array(rawAesKey), password);
}

export async function unwrapAesKeyWithPassword(envelopePem, password) {
    return decryptBytesWithPassphrase('CTOS PASSWORD PROTECTED KEY', envelopePem, password, 'Wrong password.');
}

// ── proving possession of the private key (docs/secure-drop.md) ─────────────

export async function decryptChallenge(privateKey, ciphertextBase64) {
    const plain = await crypto.subtle.decrypt({ name: 'RSA-OAEP' }, privateKey, base64ToBytes(ciphertextBase64));
    return bytesToBase64(new Uint8Array(plain));
}

// ── wrapping and unwrapping a file's AES key for one recipient's public key ─

export async function wrapAesKeyFor(publicKey, rawAesKey) {
    const wrapped = await crypto.subtle.encrypt({ name: 'RSA-OAEP' }, publicKey, rawAesKey);
    return bytesToBase64(new Uint8Array(wrapped));
}

export async function unwrapAesKey(privateKey, encryptedKeyBase64) {
    return crypto.subtle.decrypt({ name: 'RSA-OAEP' }, privateKey, base64ToBytes(encryptedKeyBase64));
}

// ── encrypting and decrypting a file with its own one-time AES-256 key ──────

export async function encryptFile(fileBytes) {
    const key = await crypto.subtle.generateKey({ name: 'AES-GCM', length: 256 }, true, ['encrypt', 'decrypt']);
    const iv = crypto.getRandomValues(new Uint8Array(12));
    const ciphertext = await crypto.subtle.encrypt({ name: 'AES-GCM', iv }, key, fileBytes);
    const rawKey = await crypto.subtle.exportKey('raw', key);

    return { ciphertext: new Uint8Array(ciphertext), iv: bytesToBase64(iv), rawKey };
}

export async function decryptFile(ciphertext, ivBase64, rawAesKey) {
    const key = await crypto.subtle.importKey('raw', rawAesKey, 'AES-GCM', false, ['decrypt']);
    const iv = base64ToBytes(ivBase64);
    return crypto.subtle.decrypt({ name: 'AES-GCM', iv }, key, ciphertext);
}

// ── a one-time password, for someone with no account yet (docs/secure-drop.md) ──

const PASSWORD_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789'; // no 0/O/1/l/I

/** 20 characters from a printable, unambiguous alphabet — plenty for a one-time, single-use secret. */
export function generatePassword() {
    const bytes = crypto.getRandomValues(new Uint8Array(20));
    return Array.from(bytes, (b) => PASSWORD_ALPHABET[b % PASSWORD_ALPHABET.length]).join('');
}
