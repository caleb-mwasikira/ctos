<?php

namespace App\Models;

use Database\Factories\UserEncryptionKeyFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * A user's Secure Drop key pair (see docs/secure-drop.md). public_key is a plain PEM; only its
 * owner's own device can ever read encrypted_private_key — it is itself an encrypted PEM,
 * unreadable to this application.
 */
#[Fillable(['user_id', 'public_key', 'encrypted_private_key', 'fingerprint'])]
class UserEncryptionKey extends Model
{
    /** @use HasFactory<UserEncryptionKeyFactory> */
    use HasFactory;

    public function user(): BelongsTo
    {
        return $this->belongsTo(User::class);
    }
}
