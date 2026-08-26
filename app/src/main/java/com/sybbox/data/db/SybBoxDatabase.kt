package com.sybbox.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.sybbox.data.db.entity.ProfileEntity
import com.sybbox.data.db.entity.RoutingRuleEntity
import com.sybbox.data.db.entity.SubscriptionEntity

@Database(
    entities = [
        ProfileEntity::class,
        SubscriptionEntity::class,
        RoutingRuleEntity::class,
    ],
    version = 5,
    exportSchema = false,
)
abstract class SybBoxDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun subscriptionDao(): SubscriptionDao
    abstract fun routingRuleDao(): RoutingRuleDao
}
