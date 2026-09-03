package com.example.data.repository

import com.example.data.local.SynonymDao
import com.example.data.local.SynonymEntity
import com.example.data.model.SynonymPair
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SynonymRepository(private val synonymDao: SynonymDao) {

    val allSynonymPairs: Flow<List<SynonymPair>> = synonymDao.getAllSynonyms().map { entities ->
        entities.map { SynonymPair(it.word, it.synonym) }
    }

    val synonymCount: Flow<Int> = synonymDao.getSynonymCount()

    suspend fun getAllPairs(): List<SynonymPair> = withContext(Dispatchers.IO) {
        synonymDao.getAllList().map { SynonymPair(it.word, it.synonym) }
    }

    suspend fun saveSynonymPairs(pairs: List<SynonymPair>) = withContext(Dispatchers.IO) {
        val entities = pairs.map { SynonymEntity(word = it.word, synonym = it.synonym) }
        synonymDao.insertAll(entities)
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        synonymDao.clearAll()
    }
}
