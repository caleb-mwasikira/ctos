<?php

namespace App\Models;

use Database\Factories\DepartmentFactory;
use Illuminate\Contracts\Encryption\DecryptException;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Attributes\Hidden;
use Illuminate\Database\Eloquent\Attributes\Scope;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Casts\Attribute;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\BelongsToMany;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Support\Facades\Crypt;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Str;

#[Fillable(['organization_id', 'name', 'slug', 'access_code'])]
#[Hidden(['access_code', 'access_code_encrypted'])]
class Department extends Model
{
    /** @use HasFactory<DepartmentFactory> */
    use HasFactory;

    /**
     * Characters used for access codes; ambiguous glyphs (0/O, 1/I) are left out.
     */
    private const ACCESS_CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

    private const ACCESS_CODE_LENGTH = 10;

    /**
     * Get the attributes that should be cast.
     *
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'access_code' => 'hashed',
        ];
    }

    /**
     * The copyable access code. It is stored encrypted, and reads as null when it cannot be
     * decrypted (for instance after the application key changed), so the page can offer to
     * generate a new code instead of failing.
     */
    protected function accessCodeEncrypted(): Attribute
    {
        return Attribute::make(
            get: function (?string $value): ?string {
                try {
                    return $value === null ? null : Crypt::decryptString($value);
                } catch (DecryptException) {
                    return null;
                }
            },
            set: fn (?string $value): ?string => $value === null ? null : Crypt::encryptString($value),
        );
    }

    /**
     * Departments whose name contains the text, ignoring case.
     *
     * The text is matched literally: a "%" or "_" typed by the user is not a wildcard.
     *
     * @param  Builder<Department>  $query
     * @return Builder<Department>
     */
    #[Scope]
    protected function nameContains(Builder $query, string $text): Builder
    {
        $escaped = str_replace(['\\', '%', '_'], ['\\\\', '\\%', '\\_'], Str::lower($text));

        return $query->whereRaw("lower(departments.name) like ? escape '\\'", ['%'.$escaped.'%']);
    }

    /**
     * Organizations and departments are addressed by their slug in URLs.
     */
    public function getRouteKeyName(): string
    {
        return 'slug';
    }

    public function organization(): BelongsTo
    {
        return $this->belongsTo(Organization::class);
    }

    public function nodes(): HasMany
    {
        return $this->hasMany(Node::class);
    }

    public function invitations(): HasMany
    {
        return $this->hasMany(DepartmentInvitation::class);
    }

    /**
     * Everyone with a place in the department, including locked members.
     */
    public function members(): BelongsToMany
    {
        return $this->belongsToMany(User::class)
            ->using(Membership::class)
            ->withPivot('role', 'locked_at')
            ->withTimestamps();
    }

    /**
     * Whether the user is locked out of this department, or of its whole organization.
     */
    public function isLockedFor(User $user): bool
    {
        return $this->members()->whereKey($user->id)->wherePivotNotNull('locked_at')->exists()
            || OrganizationLock::where('organization_id', $this->organization_id)->where('user_id', $user->id)->exists();
    }

    /**
     * Members who can currently open the department.
     */
    public function activeMembers(): BelongsToMany
    {
        return $this->members()
            ->wherePivotNull('locked_at')
            ->whereDoesntHave('organizationLocks', fn (Builder $locks) => $locks->where('organization_id', $this->organization_id));
    }

    /**
     * Generate a new random access code and return it.
     *
     * Its hash is what joining is checked against. An encrypted copy is kept as well,
     * so admins can show and copy the code later.
     */
    public function regenerateAccessCode(): string
    {
        $alphabet = self::ACCESS_CODE_ALPHABET;
        $code = '';

        for ($i = 0; $i < self::ACCESS_CODE_LENGTH; $i++) {
            $code .= $alphabet[random_int(0, strlen($alphabet) - 1)];
        }

        $this->access_code = $code;
        $this->access_code_encrypted = $code;
        $this->save();

        return $code;
    }

    public function acceptsAccessCode(string $code): bool
    {
        return Hash::check(Str::upper(trim($code)), $this->access_code);
    }

    /**
     * Build a slug that no other department uses, in any organization.
     *
     * The slug is the department's ID: what people type, with the access code, to join it.
     */
    public static function uniqueSlugFor(string $name): string
    {
        $base = Str::slug($name) ?: 'department';
        $slug = $base;

        while (static::where('slug', $slug)->exists()) {
            $slug = $base.'-'.Str::lower(Str::random(4));
        }

        return $slug;
    }
}
