<?php

namespace Database\Factories;

use App\Models\Department;
use App\Models\SecureDrop;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<SecureDrop>
 */
class SecureDropFactory extends Factory
{
    protected $model = SecureDrop::class;

    /**
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'department_id' => Department::factory(),
            'owner_id' => User::factory(),
            'name' => fake()->word().'.txt',
            'size' => fake()->numberBetween(100, 100_000),
            'storage_path' => 'secure-drops/'.fake()->uuid(),
            'iv' => base64_encode(random_bytes(12)),
        ];
    }
}
