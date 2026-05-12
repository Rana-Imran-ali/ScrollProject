<?php

namespace App\Http\Controllers;

use App\Models\MonitoredApp;
use Illuminate\Http\Request;

class MonitoredAppController extends Controller
{
    /**
     * Display a listing of the resource.
     */
    public function index(Request $request)
    {
        return response()->json([
            'status' => 'success',
            'data' => $request->user()->monitoredApps
        ]);
    }

    public function store(Request $request)
    {
        $validated = $request->validate([
            'package_name' => 'required|string',
            'app_name' => 'required|string',
            'daily_limit_minutes' => 'required|integer|min:1',
            'is_blocking_enabled' => 'boolean'
        ]);

        $app = $request->user()->monitoredApps()->create($validated);

        return response()->json(['status' => 'success', 'data' => $app], 201);
    }

    public function destroy(Request $request, $id)
    {
        $app = $request->user()->monitoredApps()->findOrFail($id);
        $app->delete();

        return response()->json(['status' => 'success', 'message' => 'App removed successfully']);
    }

    public function toggleBlocking(Request $request, $id)
    {
        $validated = $request->validate([
            'is_blocking_enabled' => 'required|boolean'
        ]);

        $app = $request->user()->monitoredApps()->findOrFail($id);
        $app->update($validated);

        return response()->json(['status' => 'success', 'data' => $app]);
    }
}
