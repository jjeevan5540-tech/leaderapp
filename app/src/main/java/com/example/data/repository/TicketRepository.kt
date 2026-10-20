package com.example.data.repository

import android.content.Context
import com.example.data.local.TicketDao
import com.example.data.model.ChatMessageEntity
import com.example.data.model.PaUserEntity
import com.example.data.model.TicketEntity
import com.example.data.model.TimelineEventEntity
import com.example.data.remote.FirestoreTicketSyncService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class TicketRepository(
    private val ticketDao: TicketDao,
    val firestoreSyncService: FirestoreTicketSyncService = FirestoreTicketSyncService(ticketDao)
) {

    init {
        firestoreSyncService.startListening()
    }

    suspend fun refresh() = withContext(Dispatchers.IO) {
        firestoreSyncService.refresh()
    }

    val allTickets: Flow<List<TicketEntity>> = ticketDao.getAllTickets()

    fun getTicketById(id: Int): Flow<TicketEntity?> = ticketDao.getTicketById(id)

    fun getChatMessages(ticketId: Int): Flow<List<ChatMessageEntity>> =
        ticketDao.getChatMessages(ticketId)

    fun getTimelineEvents(ticketId: Int): Flow<List<TimelineEventEntity>> =
        ticketDao.getTimelineEvents(ticketId)

    suspend fun deleteTicket(ticket: TicketEntity) = withContext(Dispatchers.IO) {
        ticketDao.deleteTicket(ticket)
    }

    suspend fun clearAllData(deleteFromFirestore: Boolean = true) = withContext(Dispatchers.IO) {
        ticketDao.deleteAllTickets()
        ticketDao.deleteAllChatMessages()
        ticketDao.deleteAllTimelineEvents()
        if (deleteFromFirestore) {
            firestoreSyncService.deleteAllTicketsFromFirestore()
        }
    }

    suspend fun checkAndSeedInitialData() = withContext(Dispatchers.IO) {
        ticketDao.updateVishnuAssignedPA()
        ticketDao.updateVishnuChatSender()
        ticketDao.updateVishnuTimelineActor()
    }

    fun normalizeTicketNumber(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        var upper = trimmed.uppercase()
        if (upper.startsWith("#")) upper = upper.substring(1).trim()
        return upper
    }

    fun cleanPhoneNumber(rawPhone: String): String {
        val digits = rawPhone.replace(Regex("[^0-9]"), "").trim()
        return if (digits.length >= 10) digits.takeLast(10) else digits
    }

    suspend fun markTicketAsViewed(ticket: TicketEntity) = withContext(Dispatchers.IO) {
        val updated = ticket.copy(lastViewedAt = System.currentTimeMillis())
        ticketDao.updateTicket(updated)
    }

    suspend fun mergeDuplicateTicketsByPhone() = withContext(Dispatchers.IO) {
        try {
            val all = ticketDao.getAllTicketsSync()
            if (all.size <= 1) return@withContext

            val groupedByPhone = mutableMapOf<String, MutableList<TicketEntity>>()
            for (t in all) {
                val cleanP = cleanPhoneNumber(t.phoneNumber)
                if (cleanP.isNotBlank() && cleanP.length >= 7) {
                    groupedByPhone.getOrPut(cleanP) { mutableListOf() }.add(t)
                }
            }

            for ((_, list) in groupedByPhone) {
                if (list.size > 1) {
                    // Sort by earliest creation/ID
                    val primary = list.minByOrNull { it.id } ?: list.first()
                    val duplicates = list.filter { it.id != primary.id }

                    var mergedReports = primary.reports.toMutableList()
                    var anyUnsolved = !primary.isSolved

                    for (dup in duplicates) {
                        if (!dup.isSolved) anyUnsolved = true
                        for (r in dup.reports) {
                            val isDupReport = mergedReports.any { existingR ->
                                existingR.category.equals(r.category, ignoreCase = true) &&
                                existingR.description.trim().lowercase() == r.description.trim().lowercase() &&
                                existingR.entryType.equals(r.entryType, ignoreCase = true)
                            }
                            if (!isDupReport) {
                                mergedReports.add(r)
                            }
                        }
                    }

                    mergedReports.sortBy { it.timestamp }
                    val mergedReportsJson = com.example.data.model.TicketReportJsonHelper.toJson(mergedReports)

                    val bestCitizenName = list.map { it.citizenName.trim() }
                        .firstOrNull {
                            it.isNotBlank() &&
                            !it.startsWith("Ticket #", ignoreCase = true) &&
                            !it.startsWith("Constituent", ignoreCase = true) &&
                            !it.startsWith("Citizen (", ignoreCase = true)
                        } ?: primary.citizenName

                    val bestLocation = list.map { it.location.trim() }
                        .firstOrNull { it.isNotBlank() } ?: primary.location

                    val mergedTicket = primary.copy(
                        citizenName = bestCitizenName,
                        location = bestLocation,
                        isSolved = !anyUnsolved,
                        reportsJson = mergedReportsJson
                    )

                    ticketDao.updateTicket(mergedTicket)
                    firestoreSyncService.saveTicketToFirestore(mergedTicket)

                    for (dup in duplicates) {
                        ticketDao.deleteTicket(dup)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun createTicket(
        citizenName: String,
        phoneNumber: String,
        category: String,
        subCategory: String,
        description: String,
        location: String,
        assignedPa: String,
        priority: String,
        customTicketNumber: String = "",
        entryType: String = "ISSUE"
    ): Long = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val rawPhone = phoneNumber.trim()
        val cleanPhone = cleanPhoneNumber(rawPhone)

        val normEnteredNumber = normalizeTicketNumber(customTicketNumber)

        val newReport = com.example.data.model.TicketReportEntry(
            paName = assignedPa.ifBlank { "PA" },
            category = if (category.contains("Personal", ignoreCase = true)) "Personal" else "Issue",
            subject = subCategory.ifBlank { if (category.contains("Personal", ignoreCase = true)) "Personal Request" else "Public Issue" },
            description = description.trim(),
            timestamp = now,
            entryType = entryType
        )

        // First run merge to ensure clean baseline
        mergeDuplicateTicketsByPhone()

        val allTickets = ticketDao.getAllTicketsSync()

        // Match by phone number OR ticket code (STRICT MOBILE NUMBER & TICKET ID MERGING RULE)
        var existingTicket = allTickets.firstOrNull { t ->
            val tNorm = normalizeTicketNumber(t.ticketCode)
            val codeMatch = normEnteredNumber.isNotBlank() && (
                tNorm == normEnteredNumber ||
                tNorm == "T-$normEnteredNumber" ||
                "T-$tNorm" == normEnteredNumber
            )

            val tCleanPhone = cleanPhoneNumber(t.phoneNumber)
            val phoneMatch = cleanPhone.isNotBlank() && cleanPhone.length >= 7 && tCleanPhone == cleanPhone

            codeMatch || phoneMatch
        }

        // Query Firestore for an existing document with matching ticket ID or phone BEFORE creating
        if (existingTicket == null) {
            val remoteMatch = firestoreSyncService.queryExistingTicket(customTicketNumber, phoneNumber)
            if (remoteMatch != null) {
                val rCleanPhone = cleanPhoneNumber(remoteMatch.phoneNumber)
                val rNormCode = normalizeTicketNumber(remoteMatch.ticketCode)
                val localCopy = ticketDao.getAllTicketsSync().firstOrNull { t ->
                    normalizeTicketNumber(t.ticketCode) == rNormCode ||
                    (rCleanPhone.isNotBlank() && rCleanPhone.length >= 7 && cleanPhoneNumber(t.phoneNumber) == rCleanPhone)
                }
                if (localCopy != null) {
                    existingTicket = localCopy
                } else {
                    val insertedId = ticketDao.insertTicket(remoteMatch).toInt()
                    existingTicket = remoteMatch.copy(id = insertedId)
                }
            }
        }

        if (existingTicket != null) {
            // Append new issue or report into existing ticket (never create duplicate ticket for same phone)
            val currentReports = existingTicket.reports
            val maxExistingNumber = currentReports.maxOfOrNull { it.issueNumber } ?: currentReports.size
            val reportWithNumber = if (newReport.issueNumber <= 0) {
                newReport.copy(issueNumber = maxExistingNumber + 1)
            } else newReport

            val isDuplicate = currentReports.any { r ->
                r.category.equals(reportWithNumber.category, ignoreCase = true) &&
                r.description.trim().lowercase() == reportWithNumber.description.trim().lowercase() &&
                r.entryType.equals(reportWithNumber.entryType, ignoreCase = true)
            }
            val updatedReports = if (isDuplicate) currentReports else (currentReports + reportWithNumber)
            val updatedReportsJson = com.example.data.model.TicketReportJsonHelper.toJson(updatedReports)

            // Reopen ticket if it was closed
            val wasClosed = existingTicket.isSolved
            val reopenNotice = if (wasClosed) {
                "Notice: Ticket ${existingTicket.ticketCode} was Closed and has been automatically reopened as a new issue was added by $assignedPa."
            } else existingTicket.reopenedNotice

            val bestCitizenName = if (
                citizenName.isNotBlank() &&
                !citizenName.startsWith("Ticket #", ignoreCase = true) &&
                !citizenName.startsWith("Constituent", ignoreCase = true)
            ) citizenName.trim() else existingTicket.citizenName

            val bestLocation = if (location.isNotBlank()) location.trim() else existingTicket.location

            val updatedTicket = existingTicket.copy(
                citizenName = bestCitizenName,
                location = bestLocation,
                assignedPa = assignedPa.ifBlank { existingTicket.assignedPa },
                isSolved = false, // Automatically reopen as Open / Unsolved
                solvedAt = if (wasClosed) null else existingTicket.solvedAt,
                reportsJson = updatedReportsJson,
                reopenedNotice = reopenNotice
            )
            ticketDao.updateTicket(updatedTicket)
            firestoreSyncService.saveTicketToFirestore(updatedTicket)

            // Log timeline event for addition
            val eventTitle = if (entryType.equals("REPORT", ignoreCase = true)) "New Report Submitted" else "New Issue Added"
            val timelineEvent = TimelineEventEntity(
                ticketId = existingTicket.id,
                title = eventTitle,
                description = "Entry #${updatedReports.size} by $assignedPa (${newReport.category} - ${newReport.subject}): $description",
                actor = assignedPa.ifBlank { "PA" },
                timestamp = now
            )
            ticketDao.insertTimelineEvent(timelineEvent)

            // Log chat note
            val chatMessage = ChatMessageEntity(
                ticketId = existingTicket.id,
                senderName = assignedPa.ifBlank { "PA" },
                isLeader = false,
                message = "$eventTitle by $assignedPa: ${newReport.subject} - $description",
                timestamp = now
            )
            ticketDao.insertChatMessage(chatMessage)

            return@withContext existingTicket.id.toLong()
        }

        // 3. Create new ticket with given or generated Ticket Number
        val formattedCode = when {
            normEnteredNumber.isNotBlank() -> if (normEnteredNumber.startsWith("T-")) normEnteredNumber else "T-$normEnteredNumber"
            else -> {
                val count = ticketDao.getTicketCount()
                String.format("#%03d", count + 1)
            }
        }

        val initialReports = listOf(newReport)
        val reportsJson = com.example.data.model.TicketReportJsonHelper.toJson(initialReports)

        val ticket = TicketEntity(
            ticketCode = formattedCode,
            citizenName = citizenName,
            phoneNumber = phoneNumber,
            category = category,
            subCategory = subCategory,
            description = description,
            location = location,
            assignedPa = assignedPa,
            isSolved = false, // Open / Unsolved status
            createdAt = now,
            priority = priority,
            reportsJson = reportsJson
        )
        val insertedId = ticketDao.insertTicket(ticket).toInt()
        val createdTicketEntity = ticket.copy(id = insertedId)
        firestoreSyncService.saveTicketToFirestore(createdTicketEntity)

        // Create initial timeline event
        val timelineEvent = TimelineEventEntity(
            ticketId = insertedId,
            title = "Ticket $formattedCode Created",
            description = "$category issue raised by $assignedPa: $subCategory ($description)",
            actor = assignedPa.ifBlank { "PA" },
            timestamp = now
        )
        ticketDao.insertTimelineEvent(timelineEvent)

        // Initial chat message
        val chatMessage = ChatMessageEntity(
            ticketId = insertedId,
            senderName = "System",
            isLeader = false,
            message = "Ticket $formattedCode registered for $citizenName ($phoneNumber). Raised by ${assignedPa.ifBlank { "PA" }}.",
            timestamp = now
        )
        ticketDao.insertChatMessage(chatMessage)

        insertedId.toLong()
    }

    suspend fun toggleSolvedStatus(
        ticket: TicketEntity,
        newStatus: Boolean,
        resolutionNotes: String?,
        actorName: String = "Leader Jeevan"
    ) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val updatedTicket = ticket.copy(
            isSolved = newStatus,
            solvedAt = if (newStatus) now else null,
            resolutionNotes = if (newStatus) resolutionNotes ?: ticket.resolutionNotes else null
        )
        ticketDao.updateTicket(updatedTicket)
        firestoreSyncService.saveTicketToFirestore(updatedTicket)

        val eventTitle = if (newStatus) "Marked Solved" else "Reopened as Unsolved"
        val eventDesc = if (newStatus) {
            "Status finalized as Solved by $actorName." + (if (!resolutionNotes.isNullOrBlank()) " Note: $resolutionNotes" else "")
        } else {
            "Ticket reopened by $actorName for further follow-up."
        }

        ticketDao.insertTimelineEvent(
            TimelineEventEntity(
                ticketId = ticket.id,
                title = eventTitle,
                description = eventDesc,
                actor = actorName,
                timestamp = now
            )
        )

        ticketDao.insertChatMessage(
            ChatMessageEntity(
                ticketId = ticket.id,
                senderName = actorName,
                isLeader = true,
                message = if (newStatus) {
                    "Status marked SOLVED by $actorName: ${resolutionNotes ?: "Issue addressed successfully."}"
                } else {
                    "Status updated to UNSOLVED by $actorName."
                },
                timestamp = now
            )
        )
    }

    suspend fun sendChatMessage(
        ticketId: Int,
        message: String,
        senderName: String = "Leader Jeevan",
        isLeader: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val chat = ChatMessageEntity(
            ticketId = ticketId,
            senderName = senderName,
            isLeader = isLeader,
            message = message,
            timestamp = System.currentTimeMillis()
        )
        ticketDao.insertChatMessage(chat)
    }

    // PA Credentials Management & Firestore Sync with automatic deduplication
    val allPaUsers: Flow<List<PaUserEntity>> = ticketDao.getAllPaUsers().map { list ->
        val seenKeys = mutableSetOf<String>()
        val result = mutableListOf<PaUserEntity>()
        for (pa in list) {
            val cleanPhone = pa.phoneNumber.replace(Regex("[^0-9]"), "").trim()
            val rawPhone = pa.phoneNumber.trim()
            val nameLower = pa.paName.trim().lowercase()
            val key = if (cleanPhone.isNotBlank()) "phone:$cleanPhone"
            else if (rawPhone.isNotBlank()) "phone:$rawPhone"
            else "name:$nameLower"
            if (seenKeys.add(key)) {
                result.add(pa)
            }
        }
        result
    }

    suspend fun getPaByPhone(phoneNumber: String): PaUserEntity? = withContext(Dispatchers.IO) {
        val cleanPhone = phoneNumber.replace(Regex("[^0-9]"), "").trim()
        val rawPhone = phoneNumber.trim()
        var pa = if (rawPhone.isNotBlank()) ticketDao.getPaByPhone(rawPhone) else null
        if (pa == null && cleanPhone.isNotBlank()) {
            pa = ticketDao.getPaByPhone(cleanPhone)
        }
        pa
    }

    suspend fun savePaCredential(
        paName: String,
        phoneNumber: String,
        password: String,
        designation: String = "Personal Assistant",
        status: String = "Active"
    ): Long = withContext(Dispatchers.IO) {
        val cleanPhone = phoneNumber.replace(Regex("[^0-9]"), "").trim()
        val rawPhone = phoneNumber.trim()
        val trimmedName = paName.trim()

        var existing = if (rawPhone.isNotBlank()) ticketDao.getPaByPhone(rawPhone) else null
        if (existing == null && cleanPhone.isNotBlank()) {
            existing = ticketDao.getPaByPhone(cleanPhone)
        }
        if (existing == null && trimmedName.isNotBlank()) {
            existing = ticketDao.getPaByName(trimmedName)
        }

        val paUser = PaUserEntity(
            id = existing?.id ?: 0,
            paName = trimmedName,
            phoneNumber = rawPhone,
            password = password.trim(),
            designation = designation.trim(),
            status = status,
            createdAt = existing?.createdAt ?: System.currentTimeMillis(),
            firestoreDocId = existing?.firestoreDocId
        )

        val id = if (existing != null) {
            ticketDao.updatePaUser(paUser)
            existing.id.toLong()
        } else {
            ticketDao.insertPaUser(paUser)
        }

        val savedEntity = paUser.copy(id = id.toInt())
        firestoreSyncService.savePaCredentialToFirestore(savedEntity)
        id
    }

    suspend fun updatePaCredential(paUser: PaUserEntity) = withContext(Dispatchers.IO) {
        ticketDao.updatePaUser(paUser)
        firestoreSyncService.savePaCredentialToFirestore(paUser)
    }

    suspend fun deletePaCredential(paUser: PaUserEntity) = withContext(Dispatchers.IO) {
        ticketDao.deletePaUser(paUser)
        if (paUser.phoneNumber.isNotBlank()) {
            ticketDao.deletePaUserByPhone(paUser.phoneNumber)
            val cleanPhone = paUser.phoneNumber.replace(Regex("[^0-9]"), "")
            if (cleanPhone.isNotBlank()) {
                ticketDao.deletePaUserByPhone(cleanPhone)
            }
        }
        if (paUser.paName.isNotBlank()) {
            ticketDao.deletePaUserByName(paUser.paName)
        }
        firestoreSyncService.deletePaCredentialFromFirestore(paUser)
    }

    suspend fun checkAndSeedDefaultPaUsers(context: Context) = withContext(Dispatchers.IO) {
        // Requirements: Do not add any test, dummy, or sample credentials automatically.
        // Perform a local database cleanup to remove any legacy duplicate records.
        try {
            val list = ticketDao.getAllPaUsersList()
            if (list.size > 1) {
                val seenKeys = mutableSetOf<String>()
                val duplicatesToDelete = mutableListOf<PaUserEntity>()
                for (pa in list) {
                    val cleanPhone = pa.phoneNumber.replace(Regex("[^0-9]"), "").trim()
                    val rawPhone = pa.phoneNumber.trim()
                    val nameLower = pa.paName.trim().lowercase()
                    val key = if (cleanPhone.isNotBlank()) "phone:$cleanPhone"
                    else if (rawPhone.isNotBlank()) "phone:$rawPhone"
                    else if (nameLower.isNotBlank()) "name:$nameLower"
                    else "id:${pa.id}"

                    if (!seenKeys.add(key)) {
                        duplicatesToDelete.add(pa)
                    }
                }
                for (dup in duplicatesToDelete) {
                    ticketDao.deletePaUser(dup)
                }
            }
        } catch (_: Exception) {
        }
    }
}

