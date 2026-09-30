<?php

namespace App\Models;

use Database\Factories\SecureDropFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;

/**
 * One end-to-end encrypted file, or one file of a folder shared as several of these (see
 * docs/secure-drop.md). storage_path holds AES-256-GCM ciphertext this application cannot read:
 * only a recipient's own private key, applied to their row in secure_drop_recipients, opens it.
 */
#[Fillable(['department_id', 'owner_id', 'name', 'size', 'storage_path', 'iv'])]
class SecureDrop extends Model
{
    /** @use HasFactory<SecureDropFactory> */
    use HasFactory;

    public function department(): BelongsTo
    {
        return $this->belongsTo(Department::class);
    }

    public function owner(): BelongsTo
    {
        return $this->belongsTo(User::class, 'owner_id');
    }

    public function recipients(): HasMany
    {
        return $this->hasMany(SecureDropRecipient::class);
    }
}
