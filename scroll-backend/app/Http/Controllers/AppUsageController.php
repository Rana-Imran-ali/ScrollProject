<?php

namespace App\Http\Controllers;

use App\Models\AppUsage;
use Illuminate\Http\Request;

class AppUsageController extends Controller
{
    /**
     * Display a listing of the resource.
     */
    public function syncUsage(Request $request)
    {
        $validated = $request->validate([
            'usages' => 'required|array',
            'usages.*.package_name' => 'required|string',
            'usages.*.usage_date' => 'required|date',
            'usages.*.time_spent_seconds' => 'required|integer'
        ]);

        $user = $request->user();

        foreach ($validated['usages'] as $usage) {
            $user->appUsages()->updateOrCreate(
                [
                    'package_name' => $usage['package_name'],
                    'usage_date' => $usage['usage_date'],
                ],
                [
                    'time_spent_seconds' => \DB::raw('time_spent_seconds + ' . $usage['time_spent_seconds'])
                ]
            );
        }

        return response()->json(['status' => 'success', 'message' => 'Usage synced successfully']);
    }

    public function todayAnalytics(Request $request)
    {
        $today = now()->toDateString();
        $usages = $request->user()->appUsages()->where('usage_date', $today)->get();

        return response()->json([
            'status' => 'success',
            'data' => $usages,
            'total_seconds' => $usages->sum('time_spent_seconds')
        ]);
    }

    public function weeklyReports(Request $request)
    {
        $startOfWeek = now()->startOfWeek()->toDateString();
        $usages = $request->user()->appUsages()->where('usage_date', '>=', $startOfWeek)->get();

        return response()->json([
            'status' => 'success',
            'data' => $usages
        ]);
    }
}
