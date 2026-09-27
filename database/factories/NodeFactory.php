<?php

namespace Database\Factories;

use App\Models\Department;
use App\Models\Node;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<Node>
 */
class NodeFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        $name = fake()->unique()->word().'.txt';

        return [
            'department_id' => Department::factory(),
            'path' => $name,
            'parent_path' => '',
            'name' => $name,
            'type' => Node::FILE,
            'size' => 100,
            'checksum' => hash('sha256', $name),
            'version' => 1,
            'modified_at' => now(),
        ];
    }

    /**
     * A file at the given path, however deep.
     */
    public function at(string $path): static
    {
        return $this->state(fn () => [
            'path' => $path,
            'parent_path' => str_contains($path, '/') ? dirname($path) : '',
            'name' => basename($path),
        ]);
    }

    public function folder(): static
    {
        return $this->state(fn () => ['type' => Node::FOLDER, 'size' => 0, 'checksum' => null]);
    }

    public function deleted(): static
    {
        return $this->state(fn () => ['deleted_at' => now()]);
    }
}
