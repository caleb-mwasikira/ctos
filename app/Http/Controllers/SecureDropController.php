<?php

namespace App\Http\Controllers;

use App\Models\Department;
use App\Models\SecureDrop;
use App\Models\SecureDropRecipient;
use App\Models\User;
use App\Models\UserEncryptionKey;
use App\Notifications\SecureDropReceived;
use App\Notifications\SecureDropSharedByPassword;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Gate;
use Illuminate\Support\Facades\Notification;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;
use Illuminate\View\View;
use Symfony\Component\HttpFoundation\Response as SymfonyResponse;
use Throwable;

/**
 * End-to-end encrypted files shared between people — usually the members of a department, but
 * also directly by email, to another account or to someone with none yet (see
 * docs/secure-drop.md). Every byte this controller ever touches is already ciphertext or an
 * RSA- or password-wrapped AES key made on someone else's device; it neither makes nor needs the
 * ability to read either. The one exception is a one-time password for someone with no account
 * yet ({@see send()}): the server has to see that in order to put it in their email.
 */
class SecureDropController extends Controller
{
    /** Every drop the user owns or was sent, across every department they belong to and everything sent directly by email. */
    public function index(Request $request): View
    {
        $user = $request->user();

        return view('secure-drop.index', [
            'hasKey' => $user->encryptionKey !== null,
            'sent' => SecureDrop::where('owner_id', $user->id)->with(['department', 'recipients.user'])->latest()->get(),
            'received' => SecureDrop::whereHas('recipients', fn ($query) => $query->where('user_id', $user->id))
                ->where('owner_id', '!=', $user->id)
                ->with(['department', 'owner', 'recipients' => fn ($query) => $query->where('user_id', $user->id)])
                ->latest()
                ->get(),
        ]);
    }

    /** The form to encrypt and send one, for a department the user belongs to. */
    public function create(Request $request, Department $department): View
    {
        Gate::authorize('view', $department);
        abort_if($request->user()->encryptionKey === null, 403, 'Set up your Secure Drop key first.');

        return view('secure-drop.create', [
            'department' => $department,
            'members' => $this->membersOf($department),
        ]);
    }

    /**
     * Every already-encrypted byte and key from the sender's own device: the ciphertext (one
     * upload per file; a folder is several of these sharing the same recipients), its nonce, and
     * each recipient's copy of its AES key, already wrapped with their public key. The sending
     * page calls this once per file with fetch(), so a JSON request gets a JSON answer back.
     */
    public function store(Request $request, Department $department): RedirectResponse|Response
    {
        Gate::authorize('view', $department);
        $owner = $request->user();
        abort_if($owner->encryptionKey === null, 403, 'Set up your Secure Drop key first.');

        $input = $request->validate([
            'name' => ['required', 'string', 'max:1024'],
            'ciphertext' => ['required', 'file', 'max:204800'], // 200 MB of ciphertext
            'iv' => ['required', 'string', 'size:16'], // 12 raw bytes, base64
            'recipients' => ['required', 'array', 'min:1'],
            'recipients.*.user_id' => ['required', 'integer'],
            'recipients.*.encrypted_key' => ['required', 'string'],
        ]);

        // Every recipient must actually be a department member with a key to wrap for — otherwise
        // the sender's own device could not have fetched their public key honestly. The owner is
        // always allowed too, since they are always a recipient of their own drop.
        $keyedMemberIds = $this->recipientsOf($department)->pluck('id')->push($owner->id)->all();
        foreach ($input['recipients'] as $recipient) {
            abort_unless(in_array($recipient['user_id'], $keyedMemberIds, true), 422, 'That recipient has no Secure Drop key.');
        }

        $storagePath = 'secure-drops/'.Str::uuid()->toString();
        Storage::disk('local')->put($storagePath, $request->file('ciphertext')->get());

        $drop = DB::transaction(function () use ($department, $owner, $input, $storagePath) {
            $drop = SecureDrop::create([
                'department_id' => $department->id,
                'owner_id' => $owner->id,
                'name' => $input['name'],
                'size' => $input['ciphertext']->getSize(),
                'storage_path' => $storagePath,
                'iv' => $input['iv'],
            ]);

            foreach ($input['recipients'] as $recipient) {
                $drop->recipients()->create([
                    'user_id' => $recipient['user_id'],
                    'encrypted_key' => $recipient['encrypted_key'],
                ]);
            }

            return $drop;
        });

        $this->emailRealRecipients($drop, $owner);

        if ($request->wantsJson()) {
            return response(['message' => "\"{$input['name']}\" was sent, encrypted."]);
        }

        return redirect()->route('secure-drop.index')->with('status', "\"{$input['name']}\" was sent, encrypted.");
    }

    /** Whether an email belongs to an account with a Secure Drop key — enough to send to them directly, not scoped to any department. */
    public function lookup(Request $request): Response
    {
        $input = $request->validate(['email' => ['required', 'email']]);

        $user = User::where('email', $input['email'])->first();
        if ($user === null || $user->encryptionKey === null) {
            return response(['data' => ['has_account' => false]]);
        }
        abort_if($user->id === $request->user()->id, 422, 'That is your own email.');

        return response(['data' => [
            'has_account' => true,
            'id' => $user->id,
            'name' => $user->name,
            'public_key' => $user->encryptionKey->public_key,
            'fingerprint' => $user->encryptionKey->fingerprint,
        ]]);
    }

    /**
     * Sends directly to whoever {@see lookup()} already resolved — not scoped to a department.
     * Either a real recipient (their own key wraps it, same idea as {@see store()}) or, for
     * someone with no account yet, a one-time password instead: the only place in this feature
     * the server ever sees a plaintext secret, since it has to put it in their invitation email.
     */
    public function send(Request $request): RedirectResponse|Response
    {
        $owner = $request->user();
        abort_if($owner->encryptionKey === null, 403, 'Set up your Secure Drop key first.');

        $input = $request->validate([
            'name' => ['required', 'string', 'max:1024'],
            'ciphertext' => ['required', 'file', 'max:204800'],
            'iv' => ['required', 'string', 'size:16'],
            'recipients' => ['array'],
            'recipients.*.user_id' => ['required', 'integer'],
            'recipients.*.encrypted_key' => ['required', 'string'],
            'password_recipient.email' => ['required_with:password_recipient', 'email'],
            'password_recipient.encrypted_key' => ['required_with:password_recipient', 'string'],
            'password' => ['required_with:password_recipient', 'string', 'min:8'],
        ]);

        $recipients = $input['recipients'] ?? [];
        abort_if(count($recipients) === 0 && ! isset($input['password_recipient']), 422, 'Pick at least one person to send it to.');

        // Every named recipient must actually have a Secure Drop key — the owner always does
        // (checked above); anyone else's device could only have fetched a public key to wrap for
        // honestly if that key is real.
        foreach ($recipients as $recipient) {
            $isOwner = (int) $recipient['user_id'] === $owner->id;
            abort_unless($isOwner || UserEncryptionKey::where('user_id', $recipient['user_id'])->exists(), 422, 'That recipient has no Secure Drop key.');
        }

        $storagePath = 'secure-drops/'.Str::uuid()->toString();
        Storage::disk('local')->put($storagePath, $request->file('ciphertext')->get());

        $drop = DB::transaction(function () use ($owner, $input, $storagePath, $recipients) {
            $drop = SecureDrop::create([
                'department_id' => null,
                'owner_id' => $owner->id,
                'name' => $input['name'],
                'size' => $input['ciphertext']->getSize(),
                'storage_path' => $storagePath,
                'iv' => $input['iv'],
            ]);

            foreach ($recipients as $recipient) {
                $drop->recipients()->create([
                    'user_id' => $recipient['user_id'],
                    'encrypted_key' => $recipient['encrypted_key'],
                ]);
            }

            if (isset($input['password_recipient'])) {
                $drop->recipients()->create([
                    'user_id' => null,
                    'email' => $input['password_recipient']['email'],
                    'encrypted_key' => $input['password_recipient']['encrypted_key'],
                    'access_token' => Str::random(48),
                ]);
            }

            return $drop;
        });

        $this->emailRealRecipients($drop, $owner);

        $passwordRecipient = $drop->recipients()->whereNull('user_id')->first();
        if ($passwordRecipient !== null) {
            try {
                Notification::route('mail', $passwordRecipient->email)->notify(
                    new SecureDropSharedByPassword($drop, $owner, $passwordRecipient->email, $passwordRecipient->access_token, $input['password']),
                );
            } catch (Throwable $exception) {
                report($exception);
            }
        }

        if ($request->wantsJson()) {
            return response(['message' => "\"{$input['name']}\" was sent, encrypted."]);
        }

        return redirect()->route('secure-drop.index')->with('status', "\"{$input['name']}\" was sent, encrypted.");
    }

    /** What a recipient (or the owner) needs to decrypt it: the nonce and their own wrapped key. */
    public function show(Request $request, SecureDrop $secureDrop): Response
    {
        $recipient = $this->authorizedRecipient($request, $secureDrop);

        return response(['data' => [
            'id' => $secureDrop->id,
            'name' => $secureDrop->name,
            'size' => $secureDrop->size,
            'iv' => $secureDrop->iv,
            'encrypted_key' => $recipient->encrypted_key,
        ]]);
    }

    /** The ciphertext itself; identical for every recipient, only openable with their own key. */
    public function download(Request $request, SecureDrop $secureDrop): SymfonyResponse
    {
        $this->authorizedRecipient($request, $secureDrop)->forceFill(['opened_at' => now()])->save();

        return Storage::disk('local')->download($secureDrop->storage_path, $secureDrop->name.'.enc');
    }

    public function destroy(Request $request, SecureDrop $secureDrop): RedirectResponse
    {
        abort_unless($secureDrop->owner_id === $request->user()->id, 403);

        Storage::disk('local')->delete($secureDrop->storage_path);
        $secureDrop->delete();

        return back()->with('status', "\"{$secureDrop->name}\" was deleted.");
    }

    // ── opening one sent to someone with no account (docs/secure-drop.md) ────────────────────
    // No login exists to check instead, so the unguessable token from the email link is what
    // proves this visitor is who it was sent to.

    /** The page to open what was sent, given the link from the email. */
    public function showShared(string $token): View
    {
        $recipient = SecureDropRecipient::where('access_token', $token)->with('secureDrop')->firstOrFail();

        return view('secure-drop.shared', [
            'token' => $token,
            'name' => $recipient->secureDrop->name,
            'size' => $recipient->secureDrop->size,
        ]);
    }

    /** What that page needs to decrypt it: the nonce and the password-protected key. */
    public function metaShared(string $token): Response
    {
        $recipient = SecureDropRecipient::where('access_token', $token)->with('secureDrop')->firstOrFail();

        return response(['data' => [
            'name' => $recipient->secureDrop->name,
            'size' => $recipient->secureDrop->size,
            'iv' => $recipient->secureDrop->iv,
            'encrypted_key' => $recipient->encrypted_key,
        ]]);
    }

    /** The ciphertext itself, only openable with the password from the email. */
    public function downloadShared(string $token): SymfonyResponse
    {
        $recipient = SecureDropRecipient::where('access_token', $token)->with('secureDrop')->firstOrFail();
        $recipient->forceFill(['opened_at' => now()])->save();

        return Storage::disk('local')->download($recipient->secureDrop->storage_path, $recipient->secureDrop->name.'.enc');
    }

    /** Every active member of the department, whether or not they have a Secure Drop key yet — so the sender can see who still needs to set one up. */
    private function membersOf(Department $department)
    {
        return $department->activeMembers()->with('encryptionKey')->get()
            ->map(fn ($member) => [
                'id' => $member->id,
                'name' => $member->name,
                'public_key' => $member->encryptionKey?->public_key,
                'fingerprint' => $member->encryptionKey?->fingerprint,
                'has_key' => $member->encryptionKey !== null,
            ])
            ->values();
    }

    /** The department's members who have a Secure Drop key: their public key, to wrap a file's AES key for them. */
    private function recipientsOf(Department $department)
    {
        return $department->activeMembers()->with('encryptionKey')->get()
            ->filter(fn ($member) => $member->encryptionKey !== null)
            ->map(fn ($member) => [
                'id' => $member->id,
                'name' => $member->name,
                'public_key' => $member->encryptionKey->public_key,
                'fingerprint' => $member->encryptionKey->fingerprint,
            ])
            ->values();
    }

    /** The drop's owner, or someone it was sent to — 404 either way for everyone else, so a drop's existence is not leaked. */
    private function authorizedRecipient(Request $request, SecureDrop $secureDrop): SecureDropRecipient
    {
        $recipient = $secureDrop->recipients()->where('user_id', $request->user()->id)->first();
        abort_if($recipient === null, 404);

        return $recipient;
    }

    /** Everyone but the owner (already there, and already knows) is emailed a link plus the still-encrypted file. A mail failure is reported but never undoes the send. */
    private function emailRealRecipients(SecureDrop $drop, User $owner): void
    {
        $drop->recipients()->whereNotNull('user_id')->where('user_id', '!=', $owner->id)->with('user')->get()
            ->each(fn (SecureDropRecipient $recipient) => $this->notifyUser($recipient->user, new SecureDropReceived($drop, $owner)));
    }
}
