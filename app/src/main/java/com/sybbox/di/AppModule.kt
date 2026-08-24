package com.sybbox.di

import android.content.Context
import com.sybbox.data.datastore.SettingsDataStore
import com.sybbox.data.repository.ProfileRepository
import com.sybbox.data.repository.SubscriptionRepository
import com.sybbox.data.repository.RoutingRepository
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
    fun provideProfileRepository(
        profileDao: com.sybbox.data.db.ProfileDao,
        settingsDataStore: SettingsDataStore,
    ): ProfileRepository {
        return ProfileRepository(profileDao, settingsDataStore)
    }

    @Provides
    @Singleton
    fun provideSubscriptionRepository(
        subscriptionDao: com.sybbox.data.db.SubscriptionDao,
        profileDao: com.sybbox.data.db.ProfileDao,
    ): SubscriptionRepository {
        return SubscriptionRepository(subscriptionDao, profileDao)
    }

    @Provides
    @Singleton
    fun provideRoutingRepository(
        routingRuleDao: com.sybbox.data.db.RoutingRuleDao,
    ): RoutingRepository {
        return RoutingRepository(routingRuleDao)
    }
}