<?php

namespace App\Http\Controllers;

use App\Models\FocusSession;
use Illuminate\Http\Request;

class FocusSessionController extends Controller
{
    /**
     * Display a listing of the resource.
     */
    public function startSession(Request $request)
    {
        $validated = $request->validate([
            'strict_mode' => 'boolean'
        ]);

        $activeSession = $request->user()->focusSessions()->where('status', 'active')->first();
        if ($activeSession) {
            return response()->json(['status' => 'error', 'message' => 'A focus session is already active'], 400);
        }

        $session = $request->user()->focusSessions()->create([
            'start_time' => now(),
            'status' => 'active',
            'strict_mode' => $validated['strict_mode'] ?? false
        ]);

        return response()->json(['status' => 'success', 'data' => $session], 201);
    }

    public function stopSession(Request $request)
    {
        $session = $request->user()->focusSessions()->where('status', 'active')->first();

        if (!$session) {
            return response()->json(['status' => 'error', 'message' => 'No active focus session found'], 404);
        }

        $session->update([
            'end_time' => now(),
            'status' => 'completed'
        ]);

        return response()->json(['status' => 'success', 'data' => $session]);
    }
}
