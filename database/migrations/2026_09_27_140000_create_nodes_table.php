<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * The files and folders of a department, as the cloud holds them.
     *
     * A node is addressed by its path inside the department ("reports/summary.md"); there is no
     * parent id, so renaming or moving a folder is one prefix change. A deleted node stays as a
     * tombstone (deleted_at) so that every device learns it is gone. The desktop and Android apps
     * write these rows through the Go API; the website reads them.
     */
    public function up(): void
    {
        Schema::create('nodes', function (Blueprint $table) {
            $table->id();
            $table->foreignId('department_id')->constrained()->cascadeOnDelete();
            $table->string('path', 1024);
            $table->string('parent_path', 1024)->default('');
            $table->string('name');
            $table->string('type', 10);
            $table->unsignedBigInteger('size')->default(0);
            $table->string('checksum', 64)->nullable();
            $table->unsignedInteger('version')->default(1);
            $table->string('storage_path')->nullable();
            $table->foreignId('created_by')->nullable()->constrained('users')->nullOnDelete();
            $table->foreignId('updated_by')->nullable()->constrained('users')->nullOnDelete();
            $table->timestamp('modified_at')->nullable();
            $table->timestamp('deleted_at')->nullable();
            $table->timestamps();

            $table->index(['department_id', 'parent_path']);
        });

        // Only one live node per path; a tombstone may share its path with a later live node.
        DB::statement('create unique index nodes_live_path_unique on nodes (department_id, path) where deleted_at is null');
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('nodes');
    }
};
