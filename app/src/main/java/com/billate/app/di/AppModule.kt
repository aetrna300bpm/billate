package com.billate.app.di

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import com.billate.app.data.BillRepository
import com.billate.app.data.DefaultBillRepository
import com.billate.app.data.local.BillDao
import com.billate.app.data.local.BillateDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): BillateDatabase =
        Room.databaseBuilder(context, BillateDatabase::class.java, "billate.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideBillDao(db: BillateDatabase): BillDao = db.billDao()

    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver

    @Provides
    @Singleton
    fun provideBillRepository(impl: DefaultBillRepository): BillRepository = impl
}
