package com.aiphoto.android.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity
data class CacheEntry(@PrimaryKey val key: String, val json: String, val updated: Long)

@Dao
interface CacheDao {
    @Query("SELECT * FROM CacheEntry WHERE `key` = :key")
    fun get(key: String): CacheEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(entry: CacheEntry)

    @Query("DELETE FROM CacheEntry WHERE `key` LIKE :prefix")
    fun clearPrefix(prefix: String)

    @Query("DELETE FROM CacheEntry")
    fun clear()
}

@Database(entities = [CacheEntry::class], version = 1, exportSchema = false)
abstract class CacheDatabase : RoomDatabase() {
    abstract fun entries(): CacheDao
}
