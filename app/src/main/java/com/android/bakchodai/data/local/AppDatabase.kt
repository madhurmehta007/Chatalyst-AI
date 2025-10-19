package com.android.bakchodai.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [UserEntity::class, ConversationEntity::class], version = 1, exportSchema = false)
@TypeConverters(com.android.bakchodai.data.local.TypeConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun conversationDao(): ConversationDao
}