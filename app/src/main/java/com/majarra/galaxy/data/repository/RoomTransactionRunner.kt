package com.majarra.galaxy.data.repository

import androidx.room.withTransaction
import com.majarra.galaxy.data.local.GalaxyDatabase
import com.majarra.galaxy.domain.repository.TransactionRunner
import javax.inject.Inject
import javax.inject.Singleton

/** تنفيذ المعاملة عبر Room (room-ktx يضمن سياق IO آمنا) */
@Singleton
class RoomTransactionRunner @Inject constructor(
    private val database: GalaxyDatabase
) : TransactionRunner {

    override suspend fun <T> inTransaction(block: suspend () -> T): T =
        database.withTransaction { block() }
}
