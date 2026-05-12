<?php

namespace App\Models;

use Illuminate\Database\Eloquent\Model;

class FocusSession extends Model
{
    protected $fillable = ['user_id', 'start_time', 'end_time', 'status', 'strict_mode'];

    public function user()
    {
        return $this->belongsTo(User::class);
    }
}
