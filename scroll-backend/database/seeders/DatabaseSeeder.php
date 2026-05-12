<?php

namespace Database\Seeders;

use App\Models\User;
use Illuminate\Database\Console\Seeds\WithoutModelEvents;
use Illuminate\Database\Seeder;

class DatabaseSeeder extends Seeder
{
    use WithoutModelEvents;

    /**
     * Seed the application's database.
     */
    public function run(): void
    {
        $user = User::factory()->create([
            'name' => 'Test User',
            'email' => 'test@example.com',
            'password' => \Hash::make('password')
        ]);

        $user->monitoredApps()->createMany([
            ['package_name' => 'com.instagram.android', 'app_name' => 'Instagram', 'daily_limit_minutes' => 60, 'is_blocking_enabled' => true],
            ['package_name' => 'com.zhiliaoapp.musically', 'app_name' => 'TikTok', 'daily_limit_minutes' => 30, 'is_blocking_enabled' => true],
            ['package_name' => 'com.facebook.katana', 'app_name' => 'Facebook', 'daily_limit_minutes' => 45, 'is_blocking_enabled' => false],
        ]);

        $user->appUsages()->createMany([
            ['package_name' => 'com.instagram.android', 'usage_date' => now()->toDateString(), 'time_spent_seconds' => 1200],
            ['package_name' => 'com.zhiliaoapp.musically', 'usage_date' => now()->toDateString(), 'time_spent_seconds' => 2100],
        ]);
    }
}
