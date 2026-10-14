package com.paysplit.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.paysplit.app.data.model.DistributionEntity
import com.paysplit.app.data.model.IncomeEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IncomeDao {

    @Query("SELECT * FROM incomes ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<IncomeEntity>>

    @Query("SELECT * FROM incomes ORDER BY date DESC, id DESC")
    suspend fun getAll(): List<IncomeEntity>

    @Query("SELECT * FROM incomes WHERE id = :id")
    suspend fun getById(id: Long): IncomeEntity?

    @Insert
    suspend fun insert(income: IncomeEntity): Long

    @Update
    suspend fun update(income: IncomeEntity)

    @Query("DELETE FROM incomes WHERE id = :id")
    suspend fun deleteById(id: Long)

    // --- Распределения ---

    @Query("SELECT * FROM distributions ORDER BY date DESC, id DESC")
    fun observeDistributions(): Flow<List<DistributionEntity>>

    @Query("SELECT * FROM distributions ORDER BY date DESC, id DESC")
    suspend fun getAllDistributions(): List<DistributionEntity>

    @Query("SELECT * FROM distributions WHERE incomeId = :incomeId")
    suspend fun getDistributions(incomeId: Long): List<DistributionEntity>

    @Insert
    suspend fun insertDistributions(distributions: List<DistributionEntity>)

    @Query("DELETE FROM distributions WHERE incomeId = :incomeId")
    suspend fun deleteDistributions(incomeId: Long)
}
