<?php

namespace App\Models;

use Database\Factories\OrganizationFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Attributes\Scope;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;
use Illuminate\Database\Eloquent\Relations\HasMany;
use Illuminate\Support\Str;

#[Fillable(['name', 'slug', 'owner_id'])]
class Organization extends Model
{
    /** @use HasFactory<OrganizationFactory> */
    use HasFactory;

    /**
     * Organizations the user owns, or has an unlocked place in through one of its departments.
     *
     * @param  Builder<Organization>  $query
     * @return Builder<Organization>
     */
    #[Scope]
    protected function visibleTo(Builder $query, User $user): Builder
    {
        return $query->where(function (Builder $query) use ($user) {
            $query->where('owner_id', $user->id)
                ->orWhere(function (Builder $query) use ($user) {
                    $query->whereHas('departments.members', fn (Builder $members) => $members->whereKey($user->id)->whereNull('department_user.locked_at'))
                        ->whereDoesntHave('locks', fn (Builder $locks) => $locks->where('user_id', $user->id));
                });
        });
    }

    /**
     * Organizations and departments are addressed by their slug in URLs.
     */
    public function getRouteKeyName(): string
    {
        return 'slug';
    }

    public function owner(): BelongsTo
    {
        return $this->belongsTo(User::class, 'owner_id');
    }

    public function locks(): HasMany
    {
        return $this->hasMany(OrganizationLock::class);
    }

    public function departments(): HasMany
    {
        return $this->hasMany(Department::class);
    }

    /**
     * Build a globally unique slug from the given name.
     */
    public static function uniqueSlugFor(string $name): string
    {
        $base = Str::slug($name) ?: 'organization';
        $slug = $base;

        while (static::where('slug', $slug)->exists()) {
            $slug = $base.'-'.Str::lower(Str::random(4));
        }

        return $slug;
    }
}
