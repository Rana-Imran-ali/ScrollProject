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
    fun provideDatabase(@ApplicationContext context: Context): ScrollGuardDatabase =
        ScrollGuardDatabase.getInstance(context)

    @Provides
    fun provideMonitoredAppDao(db: ScrollGuardDatabase): MonitoredAppDao =
        db.monitoredAppDao()

    // AppUsageDao, FocusSessionDao, BlockEventDao are no longer injected —
    // the simplified Repository only uses MonitoredAppDao.
}
