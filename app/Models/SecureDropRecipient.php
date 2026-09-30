<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * One person's own copy of a secure drop's AES key (see docs/secure-drop.md). Usually RSA
 * encrypted with an account's own public key (`user_id` set); for someone with no account yet,
 * it is instead encrypted with a one-time password emailed to them (`email` and `access_token`
 * set, `user_id` null) — the only place in this feature the server ever sees a plaintext secret,
 * since it is the one sending that email.
 */
#[Fillable(['secure_drop_id', 'user_id', 'email', 'encrypted_key', 'access_token'])]
class SecureDropRecipient extends Model
{
    protected $casts = [
        'opened_at' => 'datetime',
    ];

    public function secureDrop(): BelongsTo
    {
        return $this->belongsTo(SecureDrop::class);
    }

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }

    /** No account yet: opened with the one-time password emailed to `email`, via `access_token`, instead of a login. */
    public function isPasswordProtected(): bool
    {
        return $this->user_id === null;
    }
}
