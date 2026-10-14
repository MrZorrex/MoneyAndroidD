package com.paysplit.app.data.backup

import android.content.Context
import android.net.Uri
import com.paysplit.app.data.local.AppDatabase
import java.io.File

/**
 * Резервное копирование и восстановление базы данных.
 *
 * Копия создаётся командой SQLite `VACUUM INTO` — получается компактный
 * однофайловый снимок базы, который пользователь сохраняет куда угодно
 * (Google Диск, файловый менеджер и т.д.). Интернет не используется.
 */
class BackupManager(
    private val context: Context,
    private val database: AppDatabase,
) {

    /** Сохраняет копию базы в выбранный пользователем URI. */
    suspend fun backupTo(target: Uri): Result<Unit> = runCatching {
        val tempFile = File(context.cacheDir, "backups/paysplit_backup.db").apply {
            parentFile?.mkdirs()
            delete()
        }
        database.openHelper.writableDatabase.execSQL(
            "VACUUM INTO '${tempFile.absolutePath}'",
        )
        context.contentResolver.openOutputStream(target)?.use { output ->
            tempFile.inputStream().use { it.copyTo(output) }
        } ?: error("Не удалось открыть файл для записи")
        tempFile.delete()
    }

    /**
     * Восстанавливает базу из файла. После восстановления приложение
     * перезапускается, чтобы Room открыл новый файл базы.
     */
    suspend fun restoreFrom(source: Uri): Result<Unit> = runCatching {
        val dbFile = context.getDatabasePath(AppDatabase.NAME)
        dbFile.parentFile?.mkdirs()
        database.close()
        context.contentResolver.openInputStream(source)?.use { input ->
            dbFile.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Не удалось открыть файл резервной копии")
        // Убираем файлы режима WAL, чтобы SQLite открыл восстановленную базу.
        File(dbFile.absolutePath + "-wal").delete()
        File(dbFile.absolutePath + "-shm").delete()
    }
}
