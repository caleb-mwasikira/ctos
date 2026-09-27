<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * Access to a department that was shared with an email address. It is turned into a
 * membership once someone verifies that address, and then it is deleted.
 */
#[Fillable(['department_id', 'email', 'invited_by'])]
class DepartmentInvitation extends Model
{
    use HasFactory;

    public function department(): BelongsTo
    {
        return $this->belongsTo(Department::class);
    }

    public function inviter(): BelongsTo
    {
        return $this->belongsTo(User::class, 'invited_by');
    }
}
