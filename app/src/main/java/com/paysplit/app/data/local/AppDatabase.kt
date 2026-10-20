package com.paysplit.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.paysplit.app.data.model.BalanceAdjustmentEntity
import com.paysplit.app.data.model.CategoryEntity
import com.paysplit.app.data.model.DistributionEntity
import com.paysplit.app.data.model.ExpenseEntity
import com.paysplit.app.data.model.IncomeEntity
import com.paysplit.app.data.model.SettingsEntity
import com.paysplit.app.data.model.TransferEntity

@Database(
    entities = [
        CategoryEntity::class,
        IncomeEntity::class,
        DistributionEntity::class,
        ExpenseEntity::class,
        TransferEntity::class,
        BalanceAdjustmentEntity::class,
        SettingsEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun categoryDao(): CategoryDao
    abstract fun incomeDao(): IncomeDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun transferDao(): TransferDao
    abstract fun balanceAdjustmentDao(): BalanceAdjustmentDao
    abstract fun settingsDao(): SettingsDao

    companion object {
        const val NAME = "paysplit.db"

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, NAME)
                .fallbackToDestructiveMigrationOnDowngrade()
                .fallbackToDestructiveMigration()
                .build()
    }
}
