<?php

use App\Models\User;
use Database\Seeders\DatabaseSeeder;
use Illuminate\Support\Facades\Hash;

it('seeds a verified admin user', function () {
    $this->seed(DatabaseSeeder::class);

    $admin = User::firstWhere('email', 'admin@ctos.com');

    expect($admin)->not->toBeNull()
        ->and($admin->name)->toBe('Admin')
        ->and($admin->hasVerifiedEmail())->toBeTrue()
        ->and(Hash::check('0D[Zf>4(x|Cj)HMC__Ku', $admin->password))->toBeTrue();
});

it('does not reset the password of an existing admin when seeded again', function () {
    $this->seed(DatabaseSeeder::class);
    User::firstWhere('email', 'admin@ctos.com')->update(['password' => 'changed-password-123']);

    $this->seed(DatabaseSeeder::class);

    expect(User::where('email', 'admin@ctos.com')->count())->toBe(1)
        ->and(Hash::check('changed-password-123', User::firstWhere('email', 'admin@ctos.com')->password))->toBeTrue();
});
