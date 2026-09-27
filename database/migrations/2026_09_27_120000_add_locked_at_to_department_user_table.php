<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * A locked membership keeps its place in the department, but gives no access until it is unlocked.
     */
    public function up(): void
    {
        Schema::table('department_user', function (Blueprint $table) {
            $table->timestamp('locked_at')->nullable()->after('role');
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::table('department_user', function (Blueprint $table) {
            $table->dropColumn('locked_at');
        });
    }
};
