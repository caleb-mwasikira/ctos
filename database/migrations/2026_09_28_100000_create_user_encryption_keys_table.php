<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * One RSA key pair per user, for Secure Drop (see docs/secure-drop.md): the public key
     * encrypts a file for that person; only their private key can decrypt it. The private key
     * never reaches this table, or this server, in a form it can read — only the copy the
     * person's own device already encrypted with a passphrase only they know. This row is what
     * both the website and the desktop/Android app read and write, exactly like nodes and
     * departments already are.
     */
    public function up(): void
    {
        Schema::create('user_encryption_keys', function (Blueprint $table) {
            $table->id();
            $table->foreignId('user_id')->unique()->constrained()->cascadeOnDelete();
            $table->text('public_key');
            $table->text('encrypted_private_key');
            // sha256 of the public key's DER bytes, hex — shown to people so they can compare it
            // out of band, the same idea as an SSH key fingerprint.
            $table->string('fingerprint', 64);
            $table->timestamps();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('user_encryption_keys');
    }
};
