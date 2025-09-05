package ru.iplc.smart_road.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ru.iplc.smart_road.data.model.PotholeData

@Dao
interface PotholeDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(data: List<PotholeData>)

    @Query("SELECT * FROM pothole_data_v2 WHERE isSent = 0 ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    suspend fun getUnsentBatch(limit: Int, offset: Int): List<PotholeData>


    @Query("SELECT * FROM pothole_data_v2 ORDER BY timestamp ASC")
    suspend fun getAll(): List<PotholeData>

    @Query("DELETE FROM pothole_data_v2 WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    // Общее количество записей в базе
    @Query("SELECT COUNT(*) FROM pothole_data_v2")
    suspend fun getCount(): Int

    // Количество отправленных записей (если есть флаг isSent)
    @Query("SELECT COUNT(*) FROM pothole_data_v2 WHERE isSent = 1")
    suspend fun getSentCount(): Int

    // Количество новых записей, которые ещё не отправлены
    @Query("SELECT COUNT(*) FROM pothole_data_v2 WHERE isSent = 0")
    suspend fun getNewCount(): Int

    @Query("UPDATE pothole_data_v2 SET isSent = 1 WHERE id IN (:ids)")
    suspend fun markAsSent(ids: List<Long>)

    @Query("DELETE FROM pothole_data_v2 WHERE isSent = 1")
    suspend fun deleteSent()


}
