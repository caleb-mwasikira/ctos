<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Admins can copy a department's access code at any time, so it is kept encrypted (readable
     * with the application key) next to the bcrypt hash that joining is checked against.
     * Departments made before this column existed have none until their code is regenerated.
     */
    public function up(): void
    {
        Schema::table('departments', function (Blueprint $table) {
            $table->text('access_code_encrypted')->nullable()->after('access_code');
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::table('departments', function (Blueprint $table) {
            $table->dropColumn('access_code_encrypted');
        });
    }
};
