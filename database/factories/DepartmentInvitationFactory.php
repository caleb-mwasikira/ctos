<?php

namespace Database\Factories;

use App\Models\Department;
use App\Models\DepartmentInvitation;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<DepartmentInvitation>
 */
class DepartmentInvitationFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        return [
            'department_id' => Department::factory(),
            'email' => fake()->unique()->safeEmail(),
            'invited_by' => User::factory(),
        ];
    }
}
