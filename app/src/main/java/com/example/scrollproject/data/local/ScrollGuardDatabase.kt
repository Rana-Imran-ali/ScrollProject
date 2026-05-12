package com.example.scrollproject.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        MonitoredAppEntity::class,
        AppUsageEntity::class,
        FocusSessionEntity::class,
        BlockEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ScrollGuardDatabase : RoomDatabase() {
    abstract fun monitoredAppDao(): MonitoredAppDao
    abstract fun appUsageDao(): AppUsageDao
    abstract fun focusSessionDao(): FocusSessionDao
    abstract fun blockEventDao(): BlockEventDao

    companion object {
        @Volatile private var INSTANCE: ScrollGuardDatabase? = null

        fun getInstance(context: Context): ScrollGuardDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    ScrollGuardDatabase::class.java,
                    "scroll_guard.db"
                ).build().also { INSTANCE = it }
            }
    }
}
