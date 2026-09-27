<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * A department is identified by its slug alone, so people can join with just that and the
     * access code. That needs the slug to be unique across every organization, not only within one.
     */
    public function up(): void
    {
        Schema::table('departments', function (Blueprint $table) {
            $table->dropUnique(['organization_id', 'slug']);
            $table->unique('slug');
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::table('departments', function (Blueprint $table) {
            $table->dropUnique(['slug']);
            $table->unique(['organization_id', 'slug']);
        });
    }
};
