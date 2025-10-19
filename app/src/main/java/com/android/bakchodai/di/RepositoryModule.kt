package com.android.bakchodai.di

import com.android.bakchodai.data.repository.ConversationRepository
import com.android.bakchodai.data.repository.OfflineFirstConversationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class) // Install this binding in the ApplicationComponent
abstract class RepositoryModule {

    @Binds // Use @Binds for interface-to-implementation binding
    @Singleton // Ensure only one instance of the repository exists
    abstract fun bindConversationRepository(
        offlineFirstConversationRepository: OfflineFirstConversationRepository
    ): ConversationRepository
}