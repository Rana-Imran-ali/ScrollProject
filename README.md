# ScrollGuard — App Usage Limiter

An Android app that lets you set daily time limits on any installed app. When the timer expires, the app is automatically closed and you get a notification.

---

## How It Works (Simple Overview)

```
User selects app + sets limit
         ↓
TimerManager starts counting down
         ↓
AccessibilityService detects which app is open
         ↓
Timer hits 0 → app is pushed to home screen
         ↓
"Time is finished" notification appears
```

---

## File Map — Where is the Logic?

### 🧠 Core Logic (Brain of the App)

| File | What it does |
|------|-------------|
| [`core/TimerManager.kt`](app/src/main/java/com/example/scrollproject/core/TimerManager.kt) | ⭐ **Main logic.** Counts down the timer, tracks which app is in the foreground, saves progress to the database. Everything depends on this. |
| [`core/ForegroundAppDetector.kt`](app/src/main/java/com/example/scrollproject/core/ForegroundAppDetector.kt) | Detects which app is currently open using Android's UsageStats API. Used as a backup when Accessibility Service is off. |

---

### 🔔 Services (Background Workers)

| File | What it does |
|------|-------------|
| [`services/ScrollGuardAccessibilityService.kt`](app/src/main/java/com/example/scrollproject/services/ScrollGuardAccessibilityService.kt) | ⭐ **Primary enforcer.** Gets notified the instant an app opens. When the timer expires, it presses the Home button to kick the user out. |
| [`services/CountdownService.kt`](app/src/main/java/com/example/scrollproject/services/CountdownService.kt) | Foreground service that keeps the app alive in the background. Also acts as a fallback enforcer if Accessibility Service is off. |
| [`services/ExpiryNotificationManager.kt`](app/src/main/java/com/example/scrollproject/services/ExpiryNotificationManager.kt) | Posts the "Time is finished" heads-up notification. |

---

### 🖥️ UI Screens

| File | What it does |
|------|-------------|
| [`MainActivity.kt`](app/src/main/java/com/example/scrollproject/MainActivity.kt) | Entry point. Decides which screen to show: Permission Setup, Dashboard, or App Selection. |
| [`ui/compose/PermissionSetupScreen.kt`](app/src/main/java/com/example/scrollproject/ui/compose/PermissionSetupScreen.kt) | Guides user to grant Accessibility + Usage Stats permissions. |
| [`ui/compose/DashboardScreen.kt`](app/src/main/java/com/example/scrollproject/ui/compose/DashboardScreen.kt) | Main screen. Shows the time ring, monitored apps list, edit/delete controls. |
| [`ui/appselection/AppSelectionScreen.kt`](app/src/main/java/com/example/scrollproject/ui/appselection/AppSelectionScreen.kt) | App picker. Search and select an app, then set a time limit in seconds. |
| [`ui/blockscreen/BlockActivity.kt`](app/src/main/java/com/example/scrollproject/ui/blockscreen/BlockActivity.kt) | Full-screen "You're blocked" overlay. Back button goes to home, not the blocked app. |
| [`ui/compose/MonitoredAppsCompose.kt`](app/src/main/java/com/example/scrollproject/ui/compose/MonitoredAppsCompose.kt) | Small helper: displays an app icon from a Drawable. |

---

### 🗂️ Data Layer (Storage)

| File | What it does |
|------|-------------|
| [`data/local/ScrollGuardEntities.kt`](app/src/main/java/com/example/scrollproject/data/local/ScrollGuardEntities.kt) | Defines the database table (`monitored_apps`). One row per monitored app. |
| [`data/local/ScrollGuardDao.kt`](app/src/main/java/com/example/scrollproject/data/local/ScrollGuardDao.kt) | Database queries: get, insert, delete, update apps. |
| [`data/local/ScrollGuardDatabase.kt`](app/src/main/java/com/example/scrollproject/data/local/ScrollGuardDatabase.kt) | Creates/opens the Room database. |
| [`data/repository/ScrollGuardRepository.kt`](app/src/main/java/com/example/scrollproject/data/repository/ScrollGuardRepository.kt) | Middle layer between ViewModel and database. Also fetches installed apps from the device. |

---

### 📐 ViewModel (UI ↔ Logic bridge)

| File | What it does |
|------|-------------|
| [`ui/viewmodel/DashboardViewModel.kt`](app/src/main/java/com/example/scrollproject/ui/viewmodel/DashboardViewModel.kt) | Connects the Dashboard UI to TimerManager and Repository. Handles start/stop/edit/delete of monitored apps. |

---

### 🗃️ Models (Data Shapes)

| File | What it does |
|------|-------------|
| [`domain/model/Models.kt`](app/src/main/java/com/example/scrollproject/domain/model/Models.kt) | Simple data classes: `MonitoredApp`, `AppInfo`, `DashboardState`. |

---

### ⚙️ App Setup

| File | What it does |
|------|-------------|
| [`ScrollGuardApplication.kt`](app/src/main/java/com/example/scrollproject/ScrollGuardApplication.kt) | App start point. Calls `TimerManager.init()` when the app first launches. |
| [`receiver/BootReceiver.kt`](app/src/main/java/com/example/scrollproject/receiver/BootReceiver.kt) | Registered for boot events but intentionally does nothing (timers don't survive reboot). |

---

### 🗑️ Dead Code / Stubs (Can Ignore)

These files exist only so the project compiles — they do nothing:

| File | Why it's here |
|------|---------------|
| `services/UsageMonitorService.kt` | Old service replaced by `CountdownService`. Kept to avoid compile errors. |
| `services/MonitoringWorker.kt` | Old WorkManager job that was removed. No-op stub. |
| `services/AppUsageMonitorService.kt` | Prototype service never connected to anything. Unused. |
| `ui/dialog/TimeLimitDialog.kt` | Old dialog replaced by inline Compose dialogs. Unused. |
| `ui/adapter/` | Empty folder, no adapters exist. |

---

## Screen Flow

```
App Launch
    │
    ├── Permissions NOT granted → PermissionSetupScreen
    │       └── Both granted → Dashboard
    │
    └── Permissions granted → DashboardScreen
            │
            ├── Tap "+" → AppSelectionScreen
            │       └── Pick app + set seconds → back to Dashboard
            │
            └── App timer expires → Accessibility pushes to Home
                                  → "Time is finished" notification
```

---

## Permissions Required

| Permission | Why |
|-----------|-----|
| **Accessibility Service** | Detects which app is open; presses Home when timer expires |
| **Usage Stats** | Reads app usage as a fallback when Accessibility is off |
| **Notifications** | Shows the countdown notification and "Time finished" alert |

---

## Database Table: `monitored_apps`

| Column | Meaning |
|--------|---------|
| `packageName` | App identifier e.g. `com.instagram.android` |
| `appName` | Display name e.g. `Instagram` |
| `dailyLimitSeconds` | Total limit set by user |
| `remainingSeconds` | How much time is left today |
| `usedSeconds` | How much time has been used |
| `isBlocked` | `true` when time has run out |
| `lastResetDate` | Date of last daily reset (resets at midnight) |

---

## Key Design Decisions

1. **TimerManager is a singleton object** — accessible from anywhere (Service, ViewModel, Accessibility Service) without needing dependency injection.
2. **Dual enforcement** — Accessibility Service is primary (instant, no battery cost). UsageStats polling in CountdownService is the fallback.
3. **Daily reset** — Every time the app starts, TimerManager checks if `lastResetDate` is today. If not, it resets `remainingSeconds` back to the limit.
4. **Timer ticks only when the monitored app is in foreground** — If you switch to a different app, the timer pauses.
