<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Joining now asks for the access code alone, not the department's slug too — so the code has
     * to find its own department. A bcrypt hash cannot be looked up by value, only checked against
     * one already-known department, so access_code switches from bcrypt to a plain SHA-256 hex
     * digest (Department::hashAccessCode()): fine for a random, high-entropy token nobody has to
     * memorize, the same way an API key is normally hashed for lookup rather than bcrypt'd. The
     * unique index is what makes the lookup a single indexed query.
     */
    public function up(): void
    {
        Schema::table('departments', function (Blueprint $table) {
            $table->unique('access_code');
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::table('departments', function (Blueprint $table) {
            $table->dropUnique(['access_code']);
        });
    }
};
