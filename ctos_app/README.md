# CTOS app

A Kotlin Multiplatform (desktop + Android) app for CTOS. It talks to the **CTOS API**
(`../../ctos_api`, written in Go), which shares its database with the Laravel website in `../..`.

It mirrors the website: sign in, see your organizations, open one to see the departments you have
joined in it, search departments across all organizations, join a department with its ID and access
code, and open a department. A department's folder on this device is kept in sync both ways with
everyone else in it: files added, changed or deleted here go up; what others do arrives here.

```
desktop app / Android app ──► CTOS API (Go) ──► Postgres  ◄── Laravel website
      │                            │
      │                     WebSocket: "department X changed"
      └── syncs ~/CTOS/<organization>/<department> both ways
```

## What works today, and what does not

Works: creating an account and confirming the email address with the code that gets sent (no
browser involved), logging in and out, resetting a forgotten password with an emailed code (from
the login screen, or as "Change password" once signed in), organizations (including a fresh one
with no departments yet) and departments — each with its own members, access code and invitations
pages, and its own "Leave department" page — a search popup (the top bar's search icon) across
organizations, departments, and every synced department's files and folders at once, joining a
department, live refresh when the server says something changed, creating an organization,
sharing a department by email, generating its new access code, and full two-way file sync of each
department's folder — including a folder added from the app ("Add folder"), conflict copies when
both sides changed the same file, and opening a file with whatever app the device has for it. See
`data/SyncEngine.kt` (the sync itself) and `data/SyncManager.kt` (one running per department).
Uploads go over the raw-upload protocol described in the API's own README
(`../ctos_api/README.md`) rather than a plain request body, which the app's own
`network/RawUpload.kt`/`.jvm.kt` speak directly over a socket; nothing else about the app talks to
the API any other way than plain HTTP/JSON.

Also works: Secure Drop (`../../docs/secure-drop.md`), end-to-end encrypted file sharing — a key
pair generated or imported on the device itself, its private half never leaving unencrypted;
sending one file to a department's members (`screens/SendSecureDrop.kt`, showing everyone, not
just who already has a key) or directly to an email address, not scoped to any department
(`screens/SendSecureDropByEmail.kt`) — to an account with its own key, fully end-to-end, or to
someone with none yet, protected with a one-time password and an emailed invitation instead. The
app only ever sends in that last case; opening what was sent stays website-only, since a
recipient with no account has no app to open it with either way. Single files only here (not a
whole folder at once, unlike the website) — see `data/SecureDropCrypto.kt`
(`commonMain` for the `expect` API, `commonJvmMain` for the one `actual` desktop and Android
share) for the protocol itself.

Not yet: managing members (locking, assigning a lead), creating a department, and renaming an
organization or department — those still open the right page on the website (a button on the
organization page says so).

## Run it

```bash
./gradlew run                          # desktop app against the local API (http://localhost:9090/api/v1)
./gradlew run -PappEnv=production      # against the production API
./gradlew run -PapiUrl=http://localhost:9191/api/v1
```

Start the API first: `cd ../../ctos_api && go run ./cmd/api`.

| Command | What it does | API address |
|---|---|---|
| `./gradlew runLocal` | Runs the desktop app | `http://localhost:9090/api/v1` |
| `./gradlew runProduction` | Runs the desktop app | `productionApiUrl` in `composeApp/build.gradle.kts` |
| `./gradlew buildLocal` | Desktop installer + Android debug APK | local (Android emulator: `http://10.0.2.2:9090/api/v1`) |
| `./gradlew buildProduction` | Desktop installer + Android release APK | production |
| `./gradlew buildExecutable` | Runnable desktop app for this operating system (no installer, no Android SDK) | production |
| `./gradlew buildExecutableLocal` | The same | local |

Set `productionApiUrl` and the production website address (`websiteUrl`) in `composeApp/build.gradle.kts`,
or pass `-PapiUrl=...` and `-PwebsiteUrl=...`. The Android release APK is unsigned until a signing config is added.

Where things are kept on a computer: the app's data (login token, what each folder held) in
`~/.ctos/`, and the files in `~/CTOS/<organization>/<department>/`. On Android the files are in the
app's own folder on shared storage, which needs no permission.

## Tests

```bash
./gradlew :composeApp:jvmTest
```

They include real file-watcher runs on temporary folders, and draw every screen off-screen (no window
or display needed) into `composeApp/build/screenshots/*.png`, which is the quickest way to look at the UI.

A few tests run against a real API and are skipped unless given one:

```bash
CTOS_TEST_API=http://127.0.0.1:9090/api/v1 CTOS_TEST_EMAIL=alice@example.com CTOS_TEST_PASSWORD=... \
CTOS_TEST_DEPARTMENT=engineering CTOS_TEST_CODE=ABCDE23456 ./gradlew :composeApp:jvmTest --tests '*LiveApiTest*'

# two real accounts, both already members of the same department, to prove sync actually reaches
# the other person (see LiveSyncTest.kt)
CTOS_SYNC_ALICE=alice@example.com CTOS_SYNC_BOB=bob@example.com CTOS_SYNC_PASSWORD=... \
CTOS_SYNC_DEPARTMENT=engineering ./gradlew :composeApp:jvmTest -PapiUrl=http://127.0.0.1:9090/api/v1 --tests '*LiveSyncTest*'
```

`SyncEngineTest` covers the sync logic itself (conflicts, offline runs, damaged downloads, big
files...) against `FakeServer`, an in-memory stand-in for the API; uploads specifically go through a
real local server there too (see the class doc on `FakeServer`), since that is the one call the app
never makes through a mockable HTTP client at all.

## Code layout

```
composeApp/src/
  commonMain/kotlin/com/ctos/client/
    Platform.kt          what each platform does its own way (open a URL, a folder, a file, the Back button)
    network/             Dtos.kt, CtosApi.kt (the API client), RawUpload.kt (the raw-upload protocol,
                         shared part), SessionEvents.kt
    data/                AppRepository.kt (API + local database), AppViewModel.kt, Screen.kt,
                         Organizations.kt (grouping and search), SyncEngine.kt (pulls then pushes one
                         department), SyncManager.kt (one SyncEngine running per department, wired to
                         the folder watcher and the WebSocket), SyncStore.kt (what was in sync last
                         time), LocalFiles.kt (disk access the sync needs), LocalFolder.kt,
                         LocalFolderTracker.kt, FolderWatcher.kt, FolderPicker.kt ("Add folder"),
                         Explorer.kt (the file browser's own model), DepartmentEvents.kt (the
                         WebSocket), LocalDatabase.kt
    screens/             one file per screen; App.kt is the whole app; layouts/AppShell.kt is the frame
    screens/widgets/     buttons, inputs, cards, badges, dialogs, toasts
    theme/Theme.kt       colors, shapes and type: the website's brand palette, light and dark (switches
                         with the system automatically, the same as the website's own dark: classes)
  commonJvmMain/         plain java.io/java.net code shared by desktop and Android: scanning a folder,
                         LocalFiles, the raw-upload socket (RawUpload.jvm.kt)
  jvmMain/               desktop: window, SQLite, directory-watcher, opening URLs/folders/files,
                         the folder picker (JFileChooser)
  androidMain/           Android: activity, SQLite, FileObserver, Back button, opening a file
                         (FileProvider) and the folder picker (Storage Access Framework)
  commonMain/sqldelight/ the local database schema, with a real migration (migrations/) for schema changes
```

### Conventions

- Screens are split in two: a `XContent(...)` composable that only draws what it is given (no
  view model, no network) and is what the screenshot tests draw, and the wiring in `App.kt`.
- `AppRepository` is the only thing that talks to the API and to the local database.
- A failed request is a `Result.failure(ApiException)` whose message is the server's own words,
  ready to show. A 401 on anything but the login sends the user back to the login screen.

### File watching

`watchFolder` is an `expect` function. On the desktop it uses
[directory-watcher](https://github.com/gmethvin/directory-watcher) (inotify on Linux, FSEvents on
macOS, ReadDirectoryChangesW on Windows); on Android it keeps one `FileObserver` per folder, because
`FileObserver` is not recursive. The notifications are only hints: they can arrive in bursts or be
lost, so `LocalFolderTracker` waits for a quiet moment after each burst, looks at the folder again and
compares it with the last look. It also looks again every 30 seconds, and once at the start, to find
what changed while the app was closed.
