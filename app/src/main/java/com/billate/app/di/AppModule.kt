package com.billate.app.di

import android.content.ContentResolver
import android.content.Context
import androidx.room.Room
import com.billate.app.data.local.BillateDatabase
import com.billate.app.data.local.TransactionDao
import com.billate.app.data.repository.DefaultTransactionRepository
import com.billate.app.data.repository.TransactionRepository
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
    fun provideTransactionDao(db: BillateDatabase): TransactionDao = db.transactionDao()

    @Provides
    @Singleton
    fun provideContentResolver(@ApplicationContext context: Context): ContentResolver =
        context.contentResolver

    @Provides
    @Singleton
    fun provideTransactionRepository(impl: DefaultTransactionRepository): TransactionRepository =
        impl
}
