<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Two things a Secure Drop no longer has to have:
     *
     * - A department: sending directly to someone's email (see docs/secure-drop.md, "sending by
     *   email") is not scoped to any one department, so `department_id` becomes optional.
     * - Every recipient being an account with a key: someone with no account yet can still be
     *   sent a file, protected with a one-time password instead of their own key, and emailed an
     *   invitation to set one up for next time. That recipient row has an `email` (who it is for)
     *   and an `access_token` (an unguessable link, since there is no login to check instead) in
     *   place of a `user_id`.
     */
    public function up(): void
    {
        Schema::table('secure_drops', function (Blueprint $table) {
            $table->foreignId('department_id')->nullable()->change();
        });

        Schema::table('secure_drop_recipients', function (Blueprint $table) {
            $table->foreignId('user_id')->nullable()->change();
            $table->string('email')->nullable()->after('user_id');
            $table->string('access_token', 64)->nullable()->unique()->after('encrypted_key');
        });
    }

    public function down(): void
    {
        Schema::table('secure_drop_recipients', function (Blueprint $table) {
            $table->dropColumn(['email', 'access_token']);
            $table->foreignId('user_id')->nullable(false)->change();
        });

        Schema::table('secure_drops', function (Blueprint $table) {
            $table->foreignId('department_id')->nullable(false)->change();
        });
    }
};
