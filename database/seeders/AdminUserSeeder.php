<?php

namespace Database\Seeders;

use App\Models\User;
use Illuminate\Database\Seeder;

class AdminUserSeeder extends Seeder
{
    /**
     * Create the administrator account, unless it already exists.
     *
     * An existing account is left alone, so running the seeder again never
     * resets a password that has since been changed.
     */
    public function run(): void
    {
        if (User::where('email', 'admin@ctos.com')->exists()) {
            return;
        }

        User::factory()->create([
            'name' => 'Admin',
            'email' => 'admin@ctos.com',
            'password' => '0D[Zf>4(x|Cj)HMC__Ku',
        ]);
    }
}
