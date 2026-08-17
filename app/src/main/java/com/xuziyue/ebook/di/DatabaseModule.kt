package com.xuziyue.ebook.di

import android.content.Context
import androidx.room.Room
import com.xuziyue.ebook.data.AnnotationRepository
import com.xuziyue.ebook.data.AppSettingsRepository
import com.xuziyue.ebook.data.BookFileImporter
import com.xuziyue.ebook.data.BookRepository
import com.xuziyue.ebook.data.CollectionRepository
import com.xuziyue.ebook.data.EpubSecurityValidator
import com.xuziyue.ebook.data.BookmarkRepository
import com.xuziyue.ebook.data.ImportBookUseCase
import com.xuziyue.ebook.data.ImportSourceRepository
import com.xuziyue.ebook.data.ReadingProgressRepository
import com.xuziyue.ebook.data.ReadingSessionRepository
import com.xuziyue.ebook.data.export.ExportBookDataUseCase
import com.xuziyue.ebook.data.db.BookDao
import com.xuziyue.ebook.data.db.BookDatabase
import com.xuziyue.ebook.data.db.MIGRATION_1_2
import com.xuziyue.ebook.data.db.MIGRATION_2_3
import com.xuziyue.ebook.data.db.MIGRATION_3_4
import com.xuziyue.ebook.data.db.MIGRATION_4_5
import com.xuziyue.ebook.data.db.MIGRATION_5_6
import com.xuziyue.ebook.data.db.AnnotationDao
import com.xuziyue.ebook.data.db.BookmarkDao
import com.xuziyue.ebook.data.db.BookTypographyDao
import com.xuziyue.ebook.data.db.CollectionBookDao
import com.xuziyue.ebook.data.db.CollectionDao
import com.xuziyue.ebook.data.db.ImportSourceDao
import com.xuziyue.ebook.data.db.ReadingProgressDao
import com.xuziyue.ebook.data.db.ReadingSessionDao
import com.xuziyue.ebook.reader.readium.ExtractPublicationMetadataUseCase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * 数据层依赖注入（Room 数据库 + DAO + Repository + 导入编排）。
 *
 * BookFileImporter / ExtractPublicationMetadataUseCase 由 [ReaderModule] 提供，Hilt 全局图跨模块注入。
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideBookDatabase(@ApplicationContext context: Context): BookDatabase =
        Room.databaseBuilder(context, BookDatabase::class.java, BookDatabase.DB_NAME)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6) // v1→…→v6（红线 #6，不破坏性重建）
            .build()

    @Provides
    fun provideBookDao(db: BookDatabase): BookDao = db.bookDao()

    @Provides
    fun provideReadingProgressDao(db: BookDatabase): ReadingProgressDao = db.readingProgressDao()

    @Provides
    fun provideBookmarkDao(db: BookDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideAnnotationDao(db: BookDatabase): AnnotationDao = db.annotationDao()

    @Provides
    fun provideReadingSessionDao(db: BookDatabase): ReadingSessionDao = db.readingSessionDao()

    @Provides
    fun provideCollectionDao(db: BookDatabase): CollectionDao = db.collectionDao()

    @Provides
    fun provideCollectionBookDao(db: BookDatabase): CollectionBookDao = db.collectionBookDao()

    @Provides
    fun provideImportSourceDao(db: BookDatabase): ImportSourceDao = db.importSourceDao()

    @Provides
    fun provideBookTypographyDao(db: BookDatabase): BookTypographyDao = db.bookTypographyDao()

    /** 按书排版覆盖仓库（TYPE-05）。 */
    @Provides
    @Singleton
    fun provideBookTypographyRepository(dao: BookTypographyDao): com.xuziyue.ebook.data.BookTypographyRepository =
        com.xuziyue.ebook.data.BookTypographyRepository(dao)

    @Provides
    @Singleton
    fun provideImportSourceRepository(dao: ImportSourceDao): ImportSourceRepository =
        ImportSourceRepository(dao)

    @Provides
    @Singleton
    fun provideDocumentEnumerator(@ApplicationContext context: Context): com.xuziyue.ebook.data.scan.DocumentEnumerator =
        com.xuziyue.ebook.data.scan.SafDocumentEnumerator(context)

    @Provides
    @Singleton
    fun provideScanDirectoryUseCase(
        enumerator: com.xuziyue.ebook.data.scan.DocumentEnumerator,
        sourceRepository: ImportSourceRepository,
        importBookUseCase: ImportBookUseCase,
    ): com.xuziyue.ebook.data.scan.ScanDirectoryUseCase =
        com.xuziyue.ebook.data.scan.ScanDirectoryUseCase(enumerator, sourceRepository, importBookUseCase)

    @Provides
    @Singleton
    fun provideBookRepository(dao: BookDao): BookRepository = BookRepository(dao)

    @Provides
    @Singleton
    fun provideReadingProgressRepository(dao: ReadingProgressDao): ReadingProgressRepository =
        ReadingProgressRepository(dao)

    @Provides
    @Singleton
    fun provideBookmarkRepository(dao: BookmarkDao): BookmarkRepository = BookmarkRepository(dao)

    @Provides
    @Singleton
    fun provideAnnotationRepository(dao: AnnotationDao): AnnotationRepository =
        AnnotationRepository(dao)

    @Provides
    @Singleton
    fun provideCollectionRepository(
        collectionDao: CollectionDao,
        collectionBookDao: CollectionBookDao,
        bookDao: BookDao,
    ): CollectionRepository = CollectionRepository(collectionDao, collectionBookDao, bookDao)

    @Provides
    @Singleton
    fun provideReadingSessionRepository(
        dao: ReadingSessionDao,
        appSettings: AppSettingsRepository,
    ): ReadingSessionRepository = ReadingSessionRepository(dao, appSettings)

    @Provides
    @Singleton
    fun provideEpubSecurityValidator(): EpubSecurityValidator = EpubSecurityValidator()

    @Provides
    @Singleton
    fun provideImportBookUseCase(
        importer: BookFileImporter,
        extractor: ExtractPublicationMetadataUseCase,
        repo: BookRepository,
        securityValidator: EpubSecurityValidator,
        @ApplicationContext context: Context,
    ): ImportBookUseCase = ImportBookUseCase(importer, extractor, repo, securityValidator, context)

    @Provides
    @Singleton
    fun provideExportBookDataUseCase(
        bookDao: BookDao,
        annotationDao: AnnotationDao,
        bookmarkDao: BookmarkDao,
        progressDao: ReadingProgressDao,
        @ApplicationContext context: Context,
    ): ExportBookDataUseCase = ExportBookDataUseCase(bookDao, annotationDao, bookmarkDao, progressDao, context)

    @Provides
    @Singleton
    fun provideBackupUseCase(
        bookDao: BookDao,
        progressDao: ReadingProgressDao,
        bookmarkDao: BookmarkDao,
        annotationDao: AnnotationDao,
        sessionDao: ReadingSessionDao,
        collectionDao: CollectionDao,
        collectionBookDao: CollectionBookDao,
        bookTypographyDao: com.xuziyue.ebook.data.db.BookTypographyDao,
        dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
        @ApplicationContext context: Context,
    ): com.xuziyue.ebook.data.backup.BackupUseCase =
        com.xuziyue.ebook.data.backup.BackupUseCase(
            bookDao, progressDao, bookmarkDao, annotationDao, sessionDao, collectionDao, collectionBookDao, bookTypographyDao, dataStore, context,
        )

    @Provides
    @Singleton
    fun provideRestoreUseCase(
        bookDao: BookDao,
        progressDao: ReadingProgressDao,
        bookmarkDao: BookmarkDao,
        annotationDao: AnnotationDao,
        sessionDao: ReadingSessionDao,
        collectionDao: CollectionDao,
        collectionBookDao: CollectionBookDao,
        bookTypographyDao: com.xuziyue.ebook.data.db.BookTypographyDao,
        dataStore: androidx.datastore.core.DataStore<androidx.datastore.preferences.core.Preferences>,
        @ApplicationContext context: Context,
    ): com.xuziyue.ebook.data.backup.RestoreUseCase =
        com.xuziyue.ebook.data.backup.RestoreUseCase(
            bookDao, progressDao, bookmarkDao, annotationDao, sessionDao, collectionDao, collectionBookDao, bookTypographyDao, dataStore, context,
        )
}
