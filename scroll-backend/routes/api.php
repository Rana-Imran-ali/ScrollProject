<?php

use Illuminate\Http\Request;
use Illuminate\Support\Facades\Route;
use App\Http\Controllers\AuthController;
use App\Http\Controllers\MonitoredAppController;
use App\Http\Controllers\AppUsageController;
use App\Http\Controllers\FocusSessionController;

Route::post('/register', [AuthController::class, 'register']);
Route::post('/login', [AuthController::class, 'login']);

Route::middleware('auth:sanctum')->group(function () {
    Route::get('/user', function (Request $request) {
        return $request->user();
    });
    
    Route::post('/logout', [AuthController::class, 'logout']);

    // Monitored Apps
    Route::apiResource('monitored-apps', MonitoredAppController::class)->only(['index', 'store', 'destroy']);
    Route::patch('/monitored-apps/{id}/toggle', [MonitoredAppController::class, 'toggleBlocking']);

    // Screen Time & Usage
    Route::post('/usage/sync', [AppUsageController::class, 'syncUsage']);
    Route::get('/usage/today', [AppUsageController::class, 'todayAnalytics']);
    Route::get('/usage/weekly', [AppUsageController::class, 'weeklyReports']);

    // Focus Mode
    Route::post('/focus/start', [FocusSessionController::class, 'startSession']);
    Route::post('/focus/stop', [FocusSessionController::class, 'stopSession']);
});
