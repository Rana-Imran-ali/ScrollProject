<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class MonitoredApp extends Model
{
    protected $fillable = ['user_id', 'package_name', 'app_name', 'daily_limit_minutes', 'is_blocking_enabled'];

    public function user()
    {
        return $this->belongsTo(User::class);
    }
}
