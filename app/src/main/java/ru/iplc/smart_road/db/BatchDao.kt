package ru.iplc.smart_road.db

import androidx.room.*

@Dao
interface BatchDao {
    @Insert
    suspend fun insert(batch: BatchEntity): Long

    @Query("SELECT * FROM batches ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getOldest(limit: Int): List<BatchEntity>

    @Delete
    suspend fun delete(batch: BatchEntity)

    @Update
    suspend fun update(batch: BatchEntity)
}
