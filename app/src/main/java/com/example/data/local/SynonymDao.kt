package com.example.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SynonymDao {
    @Query("SELECT * FROM synonyms ORDER BY word COLLATE NOCASE ASC")
    fun getAllSynonyms(): Flow<List<SynonymEntity>>

    @Query("SELECT COUNT(*) FROM synonyms")
    fun getSynonymCount(): Flow<Int>

    @Query("SELECT * FROM synonyms")
    suspend fun getAllList(): List<SynonymEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(synonyms: List<SynonymEntity>)

    @Query("DELETE FROM synonyms")
    suspend fun clearAll()
}
