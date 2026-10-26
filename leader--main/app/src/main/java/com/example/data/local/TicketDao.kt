package com.example.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ChatMessageEntity
import com.example.data.model.PaUserEntity
import com.example.data.model.TicketEntity
import com.example.data.model.TimelineEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TicketDao {
    @Query("SELECT * FROM tickets ORDER BY createdAt DESC")
    fun getAllTickets(): Flow<List<TicketEntity>>

    @Query("SELECT * FROM tickets WHERE id = :id")
    fun getTicketById(id: Int): Flow<TicketEntity?>

    @Query("SELECT * FROM tickets WHERE ticketCode = :code LIMIT 1")
    suspend fun getTicketByCode(code: String): TicketEntity?

    @Query("SELECT * FROM tickets WHERE ticketCode = :code OR (citizenName = :name AND phoneNumber = :phone) LIMIT 1")
    suspend fun findExistingTicket(code: String, name: String, phone: String): TicketEntity?

    @Query("SELECT * FROM tickets WHERE isSolved = 0")
    suspend fun getUnsolvedTickets(): List<TicketEntity>

    @Query("DELETE FROM tickets WHERE citizenName IN (:names)")
    suspend fun deleteTicketsByCitizenNames(names: List<String>)

    @Query("SELECT COUNT(*) FROM tickets")
    suspend fun getTicketCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTicket(ticket: TicketEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTickets(tickets: List<TicketEntity>)

    @Update
    suspend fun updateTicket(ticket: TicketEntity)

    @Delete
    suspend fun deleteTicket(ticket: TicketEntity)

    @Query("DELETE FROM tickets WHERE ticketCode = :code")
    suspend fun deleteTicketByCode(code: String): Int

    @Query("DELETE FROM tickets WHERE ticketCode IN (:codes)")
    suspend fun deleteTicketsByCodes(codes: List<String>): Int

    @Query("SELECT * FROM tickets")
    suspend fun getAllTicketsSync(): List<TicketEntity>

    // Chat messages
    @Query("SELECT * FROM chat_messages WHERE ticketId = :ticketId ORDER BY timestamp ASC")
    fun getChatMessages(ticketId: Int): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessage(message: ChatMessageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatMessages(messages: List<ChatMessageEntity>)

    // Timeline events
    @Query("SELECT * FROM timeline_events WHERE ticketId = :ticketId ORDER BY timestamp ASC")
    fun getTimelineEvents(ticketId: Int): Flow<List<TimelineEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTimelineEvent(event: TimelineEventEntity): Long

    @Query("DELETE FROM tickets")
    suspend fun deleteAllTickets()

    @Query("DELETE FROM chat_messages")
    suspend fun deleteAllChatMessages()

    @Query("DELETE FROM timeline_events")
    suspend fun deleteAllTimelineEvents()

    // PA Credentials Queries
    @Query("SELECT * FROM pa_users ORDER BY createdAt DESC")
    fun getAllPaUsers(): Flow<List<PaUserEntity>>

    @Query("SELECT * FROM pa_users ORDER BY createdAt DESC")
    suspend fun getAllPaUsersList(): List<PaUserEntity>

    @Query("SELECT * FROM pa_users WHERE phoneNumber = :phone LIMIT 1")
    suspend fun getPaByPhone(phone: String): PaUserEntity?

    @Query("SELECT * FROM pa_users WHERE paName = :name LIMIT 1")
    suspend fun getPaByName(name: String): PaUserEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaUser(paUser: PaUserEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPaUsers(paUsers: List<PaUserEntity>)

    @Update
    suspend fun updatePaUser(paUser: PaUserEntity)

    @Delete
    suspend fun deletePaUser(paUser: PaUserEntity)

    @Query("DELETE FROM pa_users WHERE phoneNumber = :phone")
    suspend fun deletePaUserByPhone(phone: String): Int

    @Query("DELETE FROM pa_users WHERE paName = :name")
    suspend fun deletePaUserByName(name: String): Int

    @Query("DELETE FROM pa_users")
    suspend fun deleteAllPaUsers()

    @Query("UPDATE tickets SET assignedPA = 'Leader Jeevan' WHERE assignedPA LIKE '%Vishnu%'")
    suspend fun updateVishnuAssignedPA()

    @Query("UPDATE chat_messages SET senderName = 'Leader Jeevan' WHERE senderName LIKE '%Vishnu%'")
    suspend fun updateVishnuChatSender()

    @Query("UPDATE timeline_events SET actor = 'Leader Jeevan' WHERE actor LIKE '%Vishnu%'")
    suspend fun updateVishnuTimelineActor()
}

