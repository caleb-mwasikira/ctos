<?php

namespace App\Http\Controllers;

use App\Models\Department;
use App\Models\SecureDrop;
use App\Models\SecureDropRecipient;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Gate;
use Illuminate\Support\Facades\Storage;
use Illuminate\Support\Str;
use Illuminate\View\View;
use Symfony\Component\HttpFoundation\Response as SymfonyResponse;

/**
 * End-to-end encrypted files shared between the members of a department (see
 * docs/secure-drop.md). Every byte this controller ever touches is already ciphertext or an
 * RSA-wrapped AES key made on someone else's device; it neither makes nor needs the ability to
 * read either.
 */
class SecureDropController extends Controller
{
    /** Every drop the user owns or was sent, across every department they belong to. */
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
            'recipients' => $this->recipientsOf($department),
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

        DB::transaction(function () use ($department, $owner, $input, $storagePath) {
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
        });

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
}
