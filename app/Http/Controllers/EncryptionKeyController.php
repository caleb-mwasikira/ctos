<?php

namespace App\Http\Controllers;

use App\Models\SecureDrop;
use App\Models\SecureDropRecipient;
use App\Models\UserEncryptionKey;
use App\Support\SecureDropKey;
use Illuminate\Http\RedirectResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Storage;
use Illuminate\Validation\ValidationException;

/**
 * A person's own Secure Drop key pair: generating or importing one, proving they hold the private
 * half without this server ever seeing it, and getting the encrypted copy back if they need it on
 * another device. See docs/secure-drop.md for the whole protocol.
 */
class EncryptionKeyController extends Controller
{
    private const SESSION_KEY = 'secure_drop_challenge';

    private const CHALLENGE_LIFETIME_MINUTES = 5;

    /**
     * Step one of setting up a key pair: the device already has (made, or imported) a public key,
     * and wants proof it also holds the matching private key to be worth trusting. A random nonce
     * is encrypted with that public key and remembered against this session; only the true private
     * key can recover it.
     */
    public function challenge(Request $request): Response
    {
        $publicKey = $request->validate(['public_key' => ['required', 'string']])['public_key'];

        if ($message = SecureDropKey::checkPublicKey($publicKey)) {
            throw ValidationException::withMessages(['public_key' => $message]);
        }

        $nonce = random_bytes(32);

        $request->session()->put(self::SESSION_KEY, [
            'public_key' => $publicKey,
            'nonce' => base64_encode($nonce),
            'expires_at' => now()->addMinutes(self::CHALLENGE_LIFETIME_MINUTES)->timestamp,
        ]);

        return response(['data' => ['ciphertext' => SecureDropKey::encryptNonce($publicKey, $nonce)]]);
    }

    /**
     * Step two: the device decrypted the challenge (proving it holds the private key) and has
     * encrypted that same private key with a passphrase only its owner knows. Both are checked
     * before either half of the key pair is kept.
     */
    public function store(Request $request): RedirectResponse
    {
        $input = $request->validate([
            'proof' => ['required', 'string'],
            'encrypted_private_key' => ['required', 'string'],
        ]);

        $pending = $request->session()->get(self::SESSION_KEY);
        if (! $pending || $pending['expires_at'] < now()->timestamp) {
            throw ValidationException::withMessages(['proof' => 'That challenge has expired. Try again.']);
        }
        if (! hash_equals($pending['nonce'], trim($input['proof']))) {
            throw ValidationException::withMessages(['proof' => 'That did not prove you hold the matching private key.']);
        }
        if ($message = SecureDropKey::checkEncryptedPrivateKey($input['encrypted_private_key'])) {
            throw ValidationException::withMessages(['encrypted_private_key' => $message]);
        }

        $publicKey = $pending['public_key'];

        UserEncryptionKey::updateOrCreate(
            ['user_id' => $request->user()->id],
            [
                'public_key' => $publicKey,
                'encrypted_private_key' => $input['encrypted_private_key'],
                'fingerprint' => SecureDropKey::fingerprint($publicKey),
            ]
        );

        $request->session()->forget(self::SESSION_KEY);

        return back()->with('status', 'Your Secure Drop key is set up. Keep your passphrase somewhere safe: it is the only way to read what is shared with you.');
    }

    /** The encrypted private key back, to import into another device. Only ever their own. */
    public function download(Request $request): Response
    {
        $key = $request->user()->encryptionKey;
        abort_if($key === null, 404);

        return response($key->encrypted_private_key, 200, [
            'Content-Type' => 'application/x-pem-file',
            'Content-Disposition' => 'attachment; filename="ctos-secure-drop-key.pem"',
        ]);
    }

    /**
     * Starting over: removes the key pair and every secure drop this person sent (nobody could
     * still open them without it — they would just be permanent ciphertext otherwise) along with
     * their own copy of anything shared with them.
     */
    public function destroy(Request $request): RedirectResponse
    {
        $user = $request->user();

        DB::transaction(function () use ($user) {
            SecureDrop::where('owner_id', $user->id)->get()->each(function (SecureDrop $drop) {
                Storage::disk('local')->delete($drop->storage_path);
                $drop->delete();
            });
            SecureDropRecipient::where('user_id', $user->id)->delete();
            $user->encryptionKey()->delete();
        });

        return back()->with('status', 'Your Secure Drop key, and everything sent with it, was removed.');
    }
}
