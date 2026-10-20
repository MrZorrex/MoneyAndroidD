package com.paysplit.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.paysplit.app.data.model.BalanceAdjustmentEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BalanceAdjustmentDao {

    @Query("SELECT * FROM balance_adjustments ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<BalanceAdjustmentEntity>>

    @Query("SELECT * FROM balance_adjustments ORDER BY date DESC, id DESC")
    suspend fun getAll(): List<BalanceAdjustmentEntity>

    @Query("SELECT * FROM balance_adjustments WHERE categoryId = :categoryId ORDER BY date DESC, id DESC")
    suspend fun getForCategory(categoryId: Long): List<BalanceAdjustmentEntity>

    @Query("SELECT * FROM balance_adjustments WHERE id = :id")
    suspend fun getById(id: Long): BalanceAdjustmentEntity?

    @Insert
    suspend fun insert(entity: BalanceAdjustmentEntity): Long

    @Update
    suspend fun update(entity: BalanceAdjustmentEntity)

    @Query("DELETE FROM balance_adjustments WHERE id = :id")
    suspend fun deleteById(id: Long)
}
