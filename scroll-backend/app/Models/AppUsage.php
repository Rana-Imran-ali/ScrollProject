<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class AppUsage extends Model
{
    protected $fillable = ['user_id', 'package_name', 'usage_date', 'time_spent_seconds'];

    public function user()
    {
        return $this->belongsTo(User::class);
    }
}
