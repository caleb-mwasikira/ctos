# Secure Drop

End-to-end encrypted file (and folder) sharing between department members: a file is encrypted
on the sender's own device before it ever leaves it, and only a chosen recipient's own device can
decrypt it. Neither the website nor the Go API nor the database ever holds a private key,
passphrase, or plaintext file content — only ciphertext, and the public keys needed to make more
of it. The website and the desktop/Android app implement the exact protocol below independently
(browser Web Crypto vs. Kotlin/JVM `javax.crypto`), against the same database rows
(`user_encryption_keys`, `secure_drops`, `secure_drop_recipients`), the same way login, register
and department sync already work on both.

## Keys

Every person who wants to send or receive a Secure Drop needs an RSA-2048 key pair, generated or
imported on their own device:

- **Public key**: a standard PEM, `-----BEGIN PUBLIC KEY-----`, the key's SPKI DER, base64. Sent
  to the server as-is; anyone can have it (it is what other people encrypt *to* this person).
- **Private key**: never sent anywhere in a readable form. Before it ever leaves the device, it is
  encrypted with a passphrase only its owner knows, into the envelope below, and *that* is what
  the server stores — so a stolen database is not a stolen key.

RSA-OAEP is used throughout (`encrypt`/`decrypt` only; this key pair never signs anything), with
SHA-1 as OAEP's own padding hash — not a place SHA-1's known weaknesses apply, since nothing here
depends on collision resistance the way a signature would. SHA-1 specifically because the
website's PHP encrypts the very first challenge with `openssl_public_encrypt()`'s
`OPENSSL_PKCS1_OAEP_PADDING`, which has no way to ask for a different hash; every device speaking
this protocol (the website's Web Crypto, the API in Go, the app's `javax.crypto`) has to agree on
the one PHP is stuck with. Everything else (PBKDF2, AES-GCM, the fingerprint) still uses SHA-256.

### The encrypted private key envelope

A second kind of PEM, `-----BEGIN CTOS ENCRYPTED PRIVATE KEY-----`, whose payload (base64,
64-column wrapped, like any PEM) decodes to these bytes, in order:

| Bytes | Meaning |
|---|---|
| 1 | Format version (`1`) |
| 16 | PBKDF2 salt |
| 4 | PBKDF2 iterations, big-endian `uint32` |
| 12 | AES-GCM nonce |
| rest | AES-256-GCM ciphertext of the private key's plain PKCS#8 DER, tag included (both Web Crypto and `javax.crypto` append the 16-byte tag to the ciphertext) |

The AES-256 key is `PBKDF2-HMAC-SHA256(passphrase, salt, iterations, 32 bytes)`. 210,000
iterations is used for a freshly made envelope (OWASP's 2023 minimum for PBKDF2-SHA256); the
iteration count travels with the envelope so it can be turned up later without breaking envelopes
already handed out.

This is deliberately not the standard PKCS#8 `EncryptedPrivateKeyInfo` ASN.1 structure: that would
need a full PBES2/ASN.1 implementation to produce correctly in a browser with no library for it.
The envelope above needs only PBKDF2 and AES-GCM, which Web Crypto and `javax.crypto` both already
have, and is exactly as strong.

### Proving a private key matches a public key, without the server ever seeing either

1. The device sends the server its (new or imported) **public** key.
2. The server makes a random 32-byte nonce, encrypts it with that public key (RSA-OAEP), and
   answers with the ciphertext. It remembers the plain nonce for a few minutes, against this user
   and this public key (the website: the session; the API: an in-memory map, like its login
   throttling already uses).
3. The device decrypts the ciphertext with the **private** key it has in memory — recovering the
   nonce is only possible with the matching private key — and sends the nonce back as proof,
   alongside the passphrase-encrypted private key envelope from above.
4. The server checks the proof against what it remembered. Only then does it save the public key,
   the encrypted private key, and the public key's fingerprint (`sha256` of its DER, hex — shown
   to people so they can compare it out of band, the same idea as an SSH key fingerprint).

Generating a fresh pair or importing an existing one both end here, at step 1, so the rest of the
flow (and every later encryption) does not need to know which one happened.

### Losing the private key

The encrypted private key is downloadable from the website or the app at any time (it is, after
all, only useful with the passphrase, which the server never had) — "Download my encrypted key"
saves the exact `.pem` envelope on file, to import into a new device by supplying the passphrase
again there.

## Encrypting a file (or a folder) for one or more people

1. The sender picks a file, or several files of a folder (each keeps its path inside the folder as
   part of its `name`, e.g. "Q3 reports/summary.md" — there is no separate encrypted archive
   format for a folder, just one encrypted row per file it holds).
2. For each file: a random AES-256 key and a random 12-byte nonce are made, and the file is
   AES-256-GCM encrypted with them.
3. For every recipient chosen (department members who have set up their own keys, always
   including the sender) that same AES key is RSA-OAEP encrypted with *that person's* public key.
4. The ciphertext is uploaded once; each recipient's wrapped copy of its key goes with it
   (`secure_drop_recipients`). The server can see who a drop is shared with and how big it is, but
   never its content or its key.
5. To open one: a recipient downloads the ciphertext and their own wrapped key and nonce, decrypts
   the wrapped key with their private key (itself decrypted locally from the downloaded envelope
   with their passphrase), then AES-256-GCM decrypts the file with that key and nonce.

## Sending directly by email, not scoped to a department

Besides picking from a department's members, the website's Secure Drop page can send straight to
an email address (`SecureDropController::lookup()`/`send()`), with no department involved at all
(`secure_drops.department_id` is nullable for exactly this — it shows as "Sent by email" wherever
a department name would otherwise go). The sender's device looks the address up first:

- **An account with its own key**: sent exactly like a department send — the file's AES key is
  RSA-wrapped for their real public key, and they are emailed a link (`SecureDropReceived`), fully
  end-to-end the whole way.
- **No account yet (or none with a key)**: since there is no public key to wrap anything with, the
  AES key is instead wrapped with a one-time password (a random 20 characters), using the same
  PBKDF2 + AES-GCM envelope as an encrypted private key, just labeled
  `-----BEGIN CTOS PASSWORD PROTECTED KEY-----` instead and holding a raw AES key rather than an
  RSA one. That password, an unguessable link, and an invitation to create an account are emailed
  together (`SecureDropSharedByPassword`).

  **This is the one place the server ever sees a plaintext secret.** Every other password or
  private key in this feature only ever exists on the device that made it; this one-time password
  has to pass through the server in the clear, because the server is what sends the email
  containing it. Putting a password in the same message as what it protects is weaker than the
  key-based flow above — anyone who can read that email can open the file — but there is no way to
  do better while still supporting someone who has never set up a Secure Drop key.

  Opening it needs no login: `secure_drop_recipients.access_token` (set only for this kind of
  recipient, `user_id` left null) is an unguessable value the emailed link carries, and
  `secure-drop.shared.*` are ordinary unauthenticated routes gated by it instead of a session,
  serving a stripped-down guest page that asks for the password and decrypts in the browser the
  same way an authenticated recipient's page does.

## Where this lives

- Database: `database/migrations/2026_09_28_100000_create_user_encryption_keys_table.php`,
  `..._create_secure_drops_table.php`, `2026_09_30_120000_allow_secure_drop_without_department_or_account.php`
  (nullable `department_id`, and `email`/`access_token` for a recipient with no account).
- Website: `app/Http/Controllers/EncryptionKeyController.php`,
  `app/Http/Controllers/SecureDropController.php`, `app/Notifications/SecureDropReceived.php`,
  `app/Notifications/SecureDropSharedByPassword.php`, `resources/js/secure-drop-crypto.js` (the
  protocol above) and `resources/js/secure-drop.js` (the pages' own wiring), views under
  `resources/views/secure-drop/` (`shared.blade.php` is the unauthenticated page for someone with
  no account).
- API (`ctos_api/`): `internal/handlers/encryption_keys.go`, `internal/handlers/secure_drops.go`,
  same protocol, same tables.
- App (`ctos_app/`): `commonJvmMain/.../SecureDropCrypto.kt` (the protocol above, `javax.crypto`),
  screens under `screens/securedrop/`.
