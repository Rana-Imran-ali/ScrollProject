package com.example.scrollproject.di

import android.content.Context
import com.example.scrollproject.data.local.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ScrollGuardDatabase {
        return ScrollGuardDatabase.getInstance(context)
    }

    @Provides
    fun provideMonitoredAppDao(db: ScrollGuardDatabase): MonitoredAppDao {
        return db.monitoredAppDao()
    }

    @Provides
    fun provideAppUsageDao(db: ScrollGuardDatabase): AppUsageDao {
        return db.appUsageDao()
    }

    @Provides
    fun provideFocusSessionDao(db: ScrollGuardDatabase): FocusSessionDao {
        return db.focusSessionDao()
    }

    @Provides
    fun provideBlockEventDao(db: ScrollGuardDatabase): BlockEventDao {
        return db.blockEventDao()
    }
}
