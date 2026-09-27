<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Short numeric codes the CTOS API (not this website) emails for confirming an address or
     * resetting a password from the desktop and Android apps: a native "enter the code" screen
     * instead of a link that opens a browser. The website keeps using its own
     * password_reset_tokens table and signed links; this one is only ever read or written by the
     * API, never by this application.
     */
    public function up(): void
    {
        Schema::create('verification_codes', function (Blueprint $table) {
            $table->string('email');
            $table->string('purpose', 20); // "email_verify" or "password_reset"
            $table->string('code_hash');
            $table->timestamp('created_at')->nullable();
            $table->primary(['email', 'purpose']);
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('verification_codes');
    }
};
