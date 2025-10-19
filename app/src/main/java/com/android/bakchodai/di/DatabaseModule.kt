package com.android.bakchodai.di

import android.content.Context
import androidx.room.Room
import com.android.bakchodai.data.local.AppDatabase
import com.android.bakchodai.data.local.ConversationDao
import com.android.bakchodai.data.local.UserDao
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
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "bakchodai_database"
        ).build()
    }

    @Provides
    fun provideUserDao(appDatabase: AppDatabase): UserDao {
        return appDatabase.userDao()
    }

    @Provides
    fun provideConversationDao(appDatabase: AppDatabase): ConversationDao {
        return appDatabase.conversationDao()
    }
}