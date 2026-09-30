<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * One person's own copy of a secure drop's AES key, RSA encrypted with their public key (see
 * docs/secure-drop.md). The owner of the drop always has one of these too, so they can open their
 * own upload back up.
 */
#[Fillable(['secure_drop_id', 'user_id', 'encrypted_key'])]
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
}
