<?php

namespace Database\Factories;

use App\Models\User;
use App\Models\UserEncryptionKey;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<UserEncryptionKey>
 */
class UserEncryptionKeyFactory extends Factory
{
    protected $model = UserEncryptionKey::class;

    /**
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        $publicKey = "-----BEGIN PUBLIC KEY-----\n".base64_encode(fake()->sha256())."\n-----END PUBLIC KEY-----";

        return [
            'user_id' => User::factory(),
            'public_key' => $publicKey,
            'encrypted_private_key' => "-----BEGIN CTOS ENCRYPTED PRIVATE KEY-----\n".base64_encode(random_bytes(80))."\n-----END CTOS ENCRYPTED PRIVATE KEY-----",
            'fingerprint' => hash('sha256', $publicKey),
        ];
    }
}
