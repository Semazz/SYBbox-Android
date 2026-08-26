package com.sybbox.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.sybbox.data.db.ProfileDao
import com.sybbox.data.db.RoutingRuleDao
import com.sybbox.data.db.SubscriptionDao
import com.sybbox.data.db.SybBoxDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SybBoxDatabase {
        return Room.databaseBuilder(
            context,
            SybBoxDatabase::class.java,
            "sybbox.db"
        ).addMigrations(MIGRATION_4_5).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideProfileDao(db: SybBoxDatabase): ProfileDao = db.profileDao()

    @Provides
    fun provideSubscriptionDao(db: SybBoxDatabase): SubscriptionDao = db.subscriptionDao()

    @Provides
    fun provideRoutingRuleDao(db: SybBoxDatabase): RoutingRuleDao = db.routingRuleDao()
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE profiles ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0")
    }
}
