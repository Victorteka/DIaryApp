package com.victorteka.diaryapp.di

import android.content.Context
import androidx.room.Room
import com.example.mongo.database.ImageDatabase
import com.example.mongo.database.ImageToDeleteDao
import com.example.mongo.database.ImageToUploadDao
import com.example.util.Constants.IMAGES_DATABASE
import com.example.util.connectivity.NetworkConnectivityObserver
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
    fun provideDatabase(
        @ApplicationContext context: Context
    ): ImageDatabase {
        return Room.databaseBuilder(
            context = context,
            klass = ImageDatabase::class.java,
            name = IMAGES_DATABASE
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideFirstDao(database: ImageDatabase): ImageToUploadDao = database.imageToUploadDao()

    @Provides
    @Singleton
    fun provideSecondDao(database: ImageDatabase): ImageToDeleteDao = database.imageToDeleteDao()

    @Provides
    @Singleton
    fun provideNetworkConnectivityObserver(
        @ApplicationContext context: Context
    ) = NetworkConnectivityObserver(context = context)
}