package com.xuziyue.ebook.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.dataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.xuziyue.ebook.data.BookFileImporter
import com.xuziyue.ebook.data.ReaderTypographyRepository
import com.xuziyue.ebook.reader.readium.ExtractPublicationMetadataUseCase
import com.xuziyue.ebook.reader.readium.OpenBookUseCase
import com.xuziyue.ebook.reader.readium.OpenTxtPublicationUseCase
import com.xuziyue.ebook.reader.readium.ReadiumFacade
import com.xuziyue.ebook.reader.readium.txt.TxtEpubConverter
import com.xuziyue.ebook.reader.readium.txt.TxtParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 阅读器相关依赖注入（Hilt）。
 *
 * Phase 0 P0V-01/02/04：Readium 门面、EPUB / TXT 打开用例、DataStore、导入器、Locator 存储（均单例）。
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
    fun provideExtractPublicationMetadataUseCase(
        openBookUseCase: OpenBookUseCase,
        openTxtUseCase: OpenTxtPublicationUseCase,
    ): ExtractPublicationMetadataUseCase =
        ExtractPublicationMetadataUseCase(openBookUseCase, openTxtUseCase)

    @Provides
    @Singleton
    fun provideTxtParser(): TxtParser = TxtParser()

    @Provides
    @Singleton
    fun provideTxtEpubConverter(): TxtEpubConverter = TxtEpubConverter()

    @Provides
    @Singleton
    fun provideOpenTxtPublicationUseCase(
        facade: ReadiumFacade,
        txtParser: TxtParser,
        converter: TxtEpubConverter,
        @ApplicationContext context: Context,
    ): OpenTxtPublicationUseCase = OpenTxtPublicationUseCase(
        facade = facade,
        txtParser = txtParser,
        converter = converter,
        // TXT→EPUB 缓存目录：cacheDir 系统可回收，按 contentHash 复用（首开慢后续快）。
        cacheDir = File(context.cacheDir, "txt-converted"),
    )

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context,
    ): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { context.dataStoreFile("reader_settings.preferences_pb") },
        )

    /** 阅读排版偏好仓库（TYPE-01/02）：复用上面的全局 DataStore。 */
    @Provides
    @Singleton
    fun provideReaderTypographyRepository(
        dataStore: DataStore<Preferences>,
    ): ReaderTypographyRepository = ReaderTypographyRepository(dataStore)

    @Provides
    @Singleton
    fun provideBookFileImporter(@ApplicationContext context: Context): BookFileImporter =
        BookFileImporter(context)
}
