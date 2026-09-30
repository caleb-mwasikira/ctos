# CTOS API

The Go API behind the desktop and Android apps, and the **only** API: the Laravel website in
the parent folder (`..`) has no API routes of its own. It uses the **same Postgres database** as the website.
Laravel owns the tables (its migrations create and change them); this API only reads and writes rows.

```
desktop app / Android app ──► this API (chi) ──► Postgres  ◄── Laravel website
                                   │
                            WebSocket: "department X changed"
```

## Run it

```bash
cp .env.example .env      # fill in DB_PASSWORD
go run ./cmd/api          # listens on :9090
go run ./cmd/api --help   # usage
go run ./cmd/api --host 127.0.0.1 --port 8080
go test ./...
```

Most tests need no database. The ones in `internal/handlers/` (`api_test.go`, `files_test.go`) run the whole API against
a real database with the Laravel schema, and are skipped unless you point them at one:

```bash
# once: a scratch database with Laravel's tables
(cd .. && DB_DATABASE=ctos_test php artisan migrate --force)
TEST_DATABASE_URL="postgres://postgres:PASSWORD@127.0.0.1:5432/ctos_test?sslmode=disable" go test ./...
```

Every row those tests create is deleted again when they finish.

`--host` and `--port` override `APP_HOST` and `APP_PORT`; everything else is set with environment variables or `.env`.

## Code layout

```
cmd/api/main.go             starts everything
internal/config/            reads .env / environment
internal/database/          every SQL query, one file per table (users.go, departments.go, ...)
internal/handlers/          the HTTP handlers, one file per resource (auth.go, departments.go, ...)
    routes.go               the list of URLs -> handlers
    validate.go             every input check, as plain functions (validateEmail, ...)
internal/realtime/          WebSocket hub
internal/storage/           file contents on disk (the website reads the same folder)
internal/laravelcrypt/      decrypts/encrypts values in Laravel's `Crypt` format (needs APP_KEY)
```

### Conventions

- Plain code: no interfaces, no generic helpers. A handler reads the request, calls
  `validate…` functions, calls `app.DB.…`, and writes JSON. You never need to follow
  more than one call to see what it does.
- Handlers are grouped by **resource**.
- SQL only lives in `internal/database`. `GetX` returns one row (`nil` when there is none),
  `GetXs` a list. Functions that must change several rows together use a transaction inside.
- The tables belong to Laravel. A new table or column is a Laravel migration first.

## Responses

- Success answers `{"data": ...}`. Errors are `{"message": "..."}`.
- Validation errors are `422` with `{"message": "...", "errors": {"field": ["..."]}}`.
- Login tokens are Laravel Sanctum tokens (`personal_access_tokens`). This API issues them; the
  website deletes them all when a user resets their password, which signs every app out.
- Wrong passwords (per email) and wrong department access codes (per user) lock after five
  tries for five minutes (`429`).

## Endpoints

See `internal/handlers/routes.go`.

| Method and path | Login | What it does |
|---|---|---|
| `GET /up` | no | health check |
| `POST /api/v1/auth/login` | no | `{"email", "password", "device_name"}` → `{"data": {"token", "user"}}` |
| `POST /api/v1/auth/logout` | yes | deletes the token the request was made with |
| `POST /api/v1/auth/register` | no | `{"name", "email", "password", "password_confirmation"}` — creates an unverified account and emails it a 6-digit code; no token yet, call `verify-email` next |
| `POST /api/v1/auth/verify-email` | no | `{"email", "code", "device_name"}` — confirms the address and logs in, same as `auth/login`'s response |
| `POST /api/v1/auth/verify-email/resend` | no | `{"email"}` — emails a new code; answers the same way whether or not the address needs one |
| `POST /api/v1/auth/forgot-password` | no | `{"email"}` — emails a 6-digit code if the address has an account; answers the same way either way |
| `POST /api/v1/auth/reset-password` | no | `{"email", "code", "password", "password_confirmation"}` — sets the new password and signs every device out |
| `GET /api/v1/departments` | yes | the departments the user belongs to, with their organization and the user's role |
| `POST /api/v1/departments/join` | yes | `{"department", "code"}` — joins with the department ID and its access code |
| `GET /api/v1/organizations` | yes | organizations the user owns or belongs to, even ones with no departments yet |
| `POST /api/v1/organizations` | yes | `{"name"}` — creates one, owned by the user, with a unique slug |
| `GET /api/v1/departments/{department}` | yes | one department: members, `can_manage`; for managers also the access code, member emails and pending invitations |
| `DELETE /api/v1/departments/{department}/membership` | yes | leave the department |
| `POST /api/v1/departments/{department}/invitations` | yes | `{"email"}` — share the department (managers only); adds a verified account straight away, otherwise emails an invitation |
| `DELETE /api/v1/departments/{department}/invitations/{invitation}` | yes | withdraw a pending invitation (managers only) |
| `POST /api/v1/departments/{department}/access-code` | yes | replace the department's access code with a new random one (managers only); returns `{"access_code"}` once |
| `POST /api/v1/encryption-keys/challenge` | yes | `{"public_key"}` → `{"data": {"ciphertext"}}` — a nonce encrypted with that key, step one of setting up a Secure Drop key pair (see `docs/secure-drop.md`) |
| `POST /api/v1/encryption-keys` | yes | `{"proof", "encrypted_private_key"}` — step two: proves the challenge was decrypted, then stores the key pair |
| `GET /api/v1/encryption-keys` | yes | the caller's own key status: `{"fingerprint", "created_at", "public_key"}` |
| `GET /api/v1/encryption-keys/download` | yes | the caller's own encrypted private key PEM, to import into another device |
| `DELETE /api/v1/encryption-keys` | yes | removes the key pair and every Secure Drop it sent or received |
| `GET /api/v1/secure-drops` | yes | `{"data": {"sent": [...], "received": [...]}}` |
| `POST /api/v1/secure-drops/lookup` | yes | `{"email"}` → `{"data": {"has_account", "id"?, "name"?, "public_key"?, "fingerprint"?}}` — whether an email belongs to an account with a Secure Drop key |
| `POST /api/v1/secure-drops/send` | yes | multipart, like below but not scoped to a department: `name`, `iv`, `ciphertext`, `recipients`, plus an optional `password_recipient` (JSON `{"email","encrypted_key"}`) and `password` for someone with no account yet |
| `GET /api/v1/departments/{department}/secure-drop-recipients` | yes | every active member, with `has_key`; `public_key`/`fingerprint` only for those who have one |
| `POST /api/v1/departments/{department}/secure-drop` | yes | multipart: `name`, `iv`, `ciphertext` (file), `recipients` (JSON array of `{"user_id","encrypted_key"}`) — already encrypted on the sender's own device |
| `GET /api/v1/secure-drops/{secureDrop}` | yes | the caller's own wrapped AES key and the file's nonce, to decrypt it locally |
| `GET /api/v1/secure-drops/{secureDrop}/download` | yes | the ciphertext itself |
| `DELETE /api/v1/secure-drops/{secureDrop}` | yes | owner only |
| `GET /api/v1/departments/{department}/changes?cursor=N&limit=N` | yes | files and folders that changed after `cursor` (see below) |
| `GET /api/v1/departments/{department}/files?path=P` | yes | download a file (supports `Range`) |
| `PUT /api/v1/departments/{department}/files?path=P&base_version=N` | yes | upload a file (the request body is the content) |
| `POST /api/v1/departments/{department}/folders` | yes | `{"path"}` — create a folder (and the ones above it) |
| `DELETE /api/v1/departments/{department}/nodes?path=P&base_version=N` | yes | delete a file, or a folder with everything in it |
| `GET /api/v1/ws` | yes | WebSocket; sends `{"type": "department.changed", "data": {"department_id", "cursor"}}` |

Login answers `403` until the user has verified their email address — the link the website
emails after sign-up, or the code `auth/register` emails and `auth/verify-email` checks, for an
account made from the app — and a password reset, from the website or from `auth/reset-password`,
signs every device out by deleting its tokens. `auth/register`, `auth/verify-email/resend` and
`auth/forgot-password` all email a 6-digit code good for an hour (`verification_codes`, a table
only this API touches; the website keeps using its own signed links and `password_reset_tokens`
for its own pages).

Secure Drop (`docs/secure-drop.md`) is end-to-end encrypted files between department members, or
sent directly by email: an RSA key pair made or imported on the device itself, its private half
never leaving that device unencrypted. This API only ever stores or moves ciphertext — the public
key, the passphrase-encrypted private key, and a file's already-encrypted bytes and already
RSA- or password-wrapped AES key. The one RSA operation it performs itself,
`encryption-keys/challenge`, uses SHA-1 as OAEP's own padding hash (not a place SHA-1's known
weaknesses apply) because the website's PHP has no way to ask `openssl_public_encrypt()` for a
different one, and every device speaking this protocol has to agree on the one PHP is stuck with.
Sending directly by email to someone with no account yet is the one exception to "only ever
ciphertext": `secure-drops/send`'s `password` field is a plaintext one-time password this API
puts straight into that person's invitation email (`internal/mail`, plain text — no HTML or
attachment, unlike the website's own version of the same email) and never stores. There is no
API equivalent of the website's page for that person to actually open what was sent, since they
have no account to open this API's own app with either way.

The owner of an organization can lock a member out of one department or out of the whole
organization (`department_user.locked_at`, `organization_locks`). A locked member does not see
those departments in `GET /api/v1/departments`, is not subscribed to their WebSocket messages,
and cannot get back in by joining with the access code again (`422`).

A department ID is unique across all organizations, so joining needs only the ID and the
access code. It fails with the same `422` message whether the department or the code is
wrong, so IDs cannot be probed. Access codes are compared without regard to case
or surrounding spaces.

### Files

`{department}` is the department slug. Everyone with an active membership can read and write; anyone
else (outsiders, locked members) gets `404` from every file endpoint.

Paths are relative to the department, `/`-separated, never start or end with `/`, and have no `.`,
`..` or empty part. Folders are created automatically on the way to a file.

Every file or folder is a *node* with a `version` that goes up by one on each change. Deleting keeps the
node as a tombstone (`deleted: true`) so devices that were offline learn about it.

- **Uploading** can also ask to switch the connection out of HTTP for the transfer itself (see
  `internal/handlers/rawprotocol.go`): send `Upgrade: ctos-upload/1`, `Connection: Upgrade` and a
  Content-Length header (no chunked encoding) instead of a body, and once the server answers
  `101 Switching Protocols` write exactly that many raw bytes. The answer then comes back as one
  small frame instead of an HTTP response. A browser cannot do this (there is no way for page code
  to speak a made-up post-Upgrade protocol), so the website's own uploads use the plain path below
  unchanged; the KMM app uses the raw one for its own. Everything else about the upload — the path,
  `base_version`, the checksum and conflict rules — is identical either way.
- **Uploading** must say which version it is based on: `base_version=0` creates a new file, `N` replaces
  version `N`. If the file has moved on the answer is `409` with `{"message", "current": {...node...}}`
  (`current` is `null` when the file is gone) and nothing is changed; the client decides how to merge.
  Sending the same content again is a harmless success. Optional headers: `X-Content-SHA256` (the upload
  is refused with `422` if it does not match) and `X-Modified-At` (milliseconds; kept as the file's time).
  Files bigger than `MAX_UPLOAD_MB` are refused with `413`.
- **Changes** are a log: each change gets a number, and `GET .../changes?cursor=N` returns every node
  changed after `N` (each node once, in its latest state), the new `cursor`, and `more` when there is
  another page. Keep the cursor and ask again from it. Start with `cursor=0`.
- **Content** lives under `FILES_DIR` (the same folder as Laravel's `local` disk, `storage/app/private`),
  so the website can serve downloads. Use the same folder in both.
- **`APP_KEY`** is only needed to show managers a department's access code; without it that field is left out.

### WebSocket

The connection listens to the departments the user belonged to when it connected; after
joining a new department the app reconnects. A message is only a nudge — it never carries
file data — so the app reacts by asking the API what changed. A message a slow connection
misses is harmless for the same reason: the app catches up on its next request.
