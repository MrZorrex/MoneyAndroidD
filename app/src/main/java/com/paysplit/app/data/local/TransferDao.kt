package com.paysplit.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.paysplit.app.data.model.TransferEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {

    @Query("SELECT * FROM transfers ORDER BY date DESC, id DESC")
    fun observeAll(): Flow<List<TransferEntity>>

    @Query("SELECT * FROM transfers ORDER BY date DESC, id DESC")
    suspend fun getAll(): List<TransferEntity>

    @Query("SELECT * FROM transfers WHERE id = :id")
    suspend fun getById(id: Long): TransferEntity?

    @Insert
    suspend fun insert(transfer: TransferEntity): Long

    @Update
    suspend fun update(transfer: TransferEntity)

    @Query("DELETE FROM transfers WHERE id = :id")
    suspend fun deleteById(id: Long)
}
