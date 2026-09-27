<?php

namespace App\Models;

use Database\Factories\NodeFactory;
use Illuminate\Database\Eloquent\Attributes\Fillable;
use Illuminate\Database\Eloquent\Attributes\Scope;
use Illuminate\Database\Eloquent\Builder;
use Illuminate\Database\Eloquent\Factories\HasFactory;
use Illuminate\Database\Eloquent\Model;
use Illuminate\Database\Eloquent\Relations\BelongsTo;

/**
 * A file or a folder in a department. The Go API writes these; the website reads them.
 */
#[Fillable(['department_id', 'path', 'parent_path', 'name', 'type', 'size', 'checksum', 'version', 'storage_path', 'created_by', 'updated_by', 'modified_at'])]
class Node extends Model
{
    /** @use HasFactory<NodeFactory> */
    use HasFactory;

    public const FILE = 'file';

    public const FOLDER = 'folder';

    /**
     * Get the attributes that should be cast.
     *
     * @return array<string, string>
     */
    protected function casts(): array
    {
        return [
            'size' => 'integer',
            'version' => 'integer',
            'modified_at' => 'datetime',
            'deleted_at' => 'datetime',
        ];
    }

    public function department(): BelongsTo
    {
        return $this->belongsTo(Department::class);
    }

    /**
     * Nodes that have not been deleted.
     *
     * @param  Builder<Node>  $query
     * @return Builder<Node>
     */
    #[Scope]
    protected function live(Builder $query): Builder
    {
        return $query->whereNull('deleted_at');
    }

    public function isFolder(): bool
    {
        return $this->type === self::FOLDER;
    }

    /**
     * Whether a path is one this system accepts: relative, no empty, "." or ".." parts, no
     * backslashes or control characters. The Go API applies the same rules.
     */
    public static function isValidPath(string $path): bool
    {
        if ($path === '' || strlen($path) > 1024 || str_contains($path, '\\') || preg_match('/[\x00-\x1F\x7F]/', $path)) {
            return false;
        }

        foreach (explode('/', $path) as $segment) {
            if ($segment === '' || $segment === '.' || $segment === '..' || strlen($segment) > 255) {
                return false;
            }
        }

        return true;
    }
}
