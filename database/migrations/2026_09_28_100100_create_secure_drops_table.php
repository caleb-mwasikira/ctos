<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * A file (or, as a batch sharing the same "folder/" name prefix and recipients, several files
     * of a folder) end-to-end encrypted once with a random AES-256 key, so its content is stored
     * as ciphertext this server never has the key to read. secure_drop_recipients then holds that
     * one AES key again for each person who may open it, each copy encrypted with that person's
     * own public key — so everyone opens the very same ciphertext with their own private key.
     */
    public function up(): void
    {
        Schema::create('secure_drops', function (Blueprint $table) {
            $table->id();
            $table->foreignId('department_id')->constrained()->cascadeOnDelete();
            $table->foreignId('owner_id')->constrained('users')->cascadeOnDelete();
            // The original name ("payroll.xlsx", or "Q3 reports/summary.md" for a folder's file).
            $table->string('name');
            $table->unsignedBigInteger('size');
            // Where the AES-256-GCM ciphertext is kept (Storage::disk('local')).
            $table->string('storage_path');
            // The GCM nonce used to encrypt it, base64.
            $table->string('iv', 24);
            $table->timestamps();

            $table->index(['department_id', 'owner_id']);
        });

        Schema::create('secure_drop_recipients', function (Blueprint $table) {
            $table->id();
            $table->foreignId('secure_drop_id')->constrained()->cascadeOnDelete();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            // The drop's AES key, RSA-OAEP encrypted with this recipient's public key, base64.
            // The owner is always a recipient of their own drop too, so they can open it back up.
            $table->text('encrypted_key');
            $table->timestamp('opened_at')->nullable();
            $table->timestamps();

            $table->unique(['secure_drop_id', 'user_id']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('secure_drop_recipients');
        Schema::dropIfExists('secure_drops');
    }
};
