package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.entity.EventType
import com.example.data.entity.MessageRecord
import com.example.data.entity.MessageStatus
import com.example.data.entity.SendChannel
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {
    @Query("SELECT * FROM message_records ORDER BY createdAt DESC")
    fun getAllMessages(): Flow<List<MessageRecord>>

    @Query("SELECT * FROM message_records WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getMessagesByStatus(status: MessageStatus): List<MessageRecord>

    @Query("SELECT * FROM message_records WHERE id = :id LIMIT 1")
    suspend fun getMessageById(id: Long): MessageRecord?

    @Query("SELECT * FROM message_records WHERE studentId = :studentId AND date = :date AND eventType = :eventType AND channel = :channel LIMIT 1")
    suspend fun findExistingRecord(
        studentId: Long,
        date: String,
        eventType: EventType,
        channel: SendChannel
    ): MessageRecord?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessage(messageRecord: MessageRecord): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMessages(messageRecords: List<MessageRecord>): List<Long>

    @Update
    suspend fun updateMessage(messageRecord: MessageRecord)

    @Query("UPDATE message_records SET status = :status, sentAt = :sentAt, attempts = attempts + 1, errorMessage = :errorMessage WHERE id = :id")
    suspend fun updateMessageStatus(id: Long, status: MessageStatus, sentAt: Long?, errorMessage: String?)
}
