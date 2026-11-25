package com.forge.imperiale.di

import android.content.Context
import androidx.room.Room
import com.forge.imperiale.coach.CoachEngine
import com.forge.imperiale.coach.RuleBasedCoachEngine
import com.forge.imperiale.data.local.ForgeDatabase
import com.google.gson.Gson
import com.google.gson.GsonBuilder
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
    fun provideForgeDatabase(@ApplicationContext context: Context): ForgeDatabase =
        Room.databaseBuilder(
            context,
            ForgeDatabase::class.java,
            "forge_imperiale.db"
        )
            .addMigrations(ForgeDatabase.MIGRATION_1_2)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides
    @Singleton
    fun provideGson(): Gson = GsonBuilder().serializeNulls().create()

    @Provides
    @Singleton
    fun provideCoachEngine(): CoachEngine = RuleBasedCoachEngine()
}
