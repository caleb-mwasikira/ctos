<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * A log of every change to a department's nodes. Its ids only ever grow, so a device that
     * remembers the last id it saw (its cursor) can ask for everything after it.
     */
    public function up(): void
    {
        Schema::create('node_changes', function (Blueprint $table) {
            $table->id();
            $table->foreignId('department_id')->constrained()->cascadeOnDelete();
            $table->foreignId('node_id')->constrained('nodes')->cascadeOnDelete();
            $table->timestamp('created_at')->useCurrent();

            $table->index(['department_id', 'id']);
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('node_changes');
    }
};
