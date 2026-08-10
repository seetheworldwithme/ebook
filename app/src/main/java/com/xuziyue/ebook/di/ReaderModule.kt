package com.xuziyue.ebook.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.xuziyue.ebook.data.BookFileImporter
import com.xuziyue.ebook.data.LocatorStore
import com.xuziyue.ebook.reader.readium.OpenBookUseCase
import com.xuziyue.ebook.reader.readium.ReadiumFacade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 阅读器相关依赖注入（Hilt）。
 *
 * Phase 0 P0V-01/02：Readium 门面、打开用例、DataStore、导入器、Locator 存储（均单例）。
 */
@Module
@InstallIn(SingletonComponent::class)
object ReaderModule {

    @Provides
    @Singleton
    fun provideReadiumFacade(@ApplicationContext context: Context): ReadiumFacade =
        ReadiumFacade(context)

    @Provides
    @Singleton
    fun provideOpenBookUseCase(facade: ReadiumFacade): OpenBookUseCase =
        OpenBookUseCase(facade)

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { context.dataStoreFile("reader_settings.preferences_pb") },
        )

    @Provides
    @Singleton
    fun provideBookFileImporter(@ApplicationContext context: Context): BookFileImporter =
        BookFileImporter(context)

    @Provides
    @Singleton
    fun provideLocatorStore(dataStore: DataStore<Preferences>): LocatorStore =
        LocatorStore(dataStore)
}
