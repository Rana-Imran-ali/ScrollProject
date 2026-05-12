package com.example.scrollproject.core

import com.example.scrollproject.data.repository.ScrollGuardRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object TimerManager {
    private var repository: ScrollGuardRepository? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var activePackage: String? = null
        private set
    
    // Live usage map (PackageName -> TimeSpent in seconds for today)
    private val _liveUsages = MutableStateFlow<Map<String, Long>>(emptyMap())
    val liveUsages: StateFlow<Map<String, Long>> = _liveUsages.asStateFlow()
    
    private var timerJob: Job? = null

    fun initialize(repo: ScrollGuardRepository) {
        if (repository != null) return
        repository = repo
        
        // Initial load from DB (One-time)
        scope.launch {
            val initialUsages = repo.getTodayUsageOnce()
            val map = initialUsages.associate { it.packageName to it.timeSpentSeconds }
            _liveUsages.update { it + map }
        }
        
        startTimer()
    }

    fun setActivePackage(pkg: String?) {
        activePackage = if (pkg.isNullOrEmpty()) null else pkg
    }

    fun updateLiveUsage(pkg: String, totalSeconds: Long) {
        _liveUsages.update { currentMap ->
            val existing = currentMap[pkg] ?: 0L
            if (totalSeconds > existing) {
                currentMap + (pkg to totalSeconds)
            } else {
                currentMap
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = scope.launch {
            while (isActive) {
                delay(1000)
                val currentPkg = activePackage
                if (currentPkg != null) {
                    // Update live map immediately for UI
                    _liveUsages.update { currentMap ->
                        val newMap = currentMap.toMutableMap()
                        val currentUsage = newMap[currentPkg] ?: 0L
                        newMap[currentPkg] = currentUsage + 1
                        newMap
                    }
                }
            }
        }
    }
}

