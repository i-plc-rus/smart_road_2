package ru.iplc.smart_road.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ru.iplc.smart_road.data.model.PotholeData

@Database(
    entities = [PotholeData::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun potholeDao(): PotholeDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pothole_db_v2_1_26"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}