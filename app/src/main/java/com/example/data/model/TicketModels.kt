package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

enum class TicketCategory(val displayName: String) {
    PERSONAL("Personal"),
    ISSUE("Issue")
}

enum class TicketPriority(val label: String) {
    HIGH("High"),
    MEDIUM("Medium"),
    LOW("Low")
}

/**
 * An individual issue or report entry for a ticket.
 * Every issue and report under a ticket is preserved.
 */
data class TicketReportEntry(
    val id: String = UUID.randomUUID().toString(),
    val paName: String,
    val category: String, // "Personal" or "Issue"
    val subject: String,  // sub-category / issue title
    val description: String,
    val timestamp: Long = System.currentTimeMillis(),
    val entryType: String = "ISSUE", // "ISSUE" or "REPORT"
    val issueNumber: Int = 0 // Serial issue number (e.g. 1, 2, 3, 4, 5...)
) {
    val isIssue: Boolean
        get() = entryType.equals("ISSUE", ignoreCase = true)

    val formattedDateTime: String
        get() {
            val sdf = SimpleDateFormat("dd-MM-yyyy, hh:mm a", Locale.ENGLISH)
            return sdf.format(Date(timestamp))
        }
}

object TicketReportJsonHelper {
    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()
    private val listType = Types.newParameterizedType(List::class.java, TicketReportEntry::class.java)
    private val adapter = moshi.adapter<List<TicketReportEntry>>(listType)

    fun toJson(list: List<TicketReportEntry>): String = try {
        adapter.toJson(list)
    } catch (_: Exception) {
        ""
    }

    fun fromJson(json: String?): List<TicketReportEntry> = try {
        if (json.isNullOrBlank()) emptyList() else adapter.fromJson(json) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }
}

@Entity(tableName = "tickets")
data class TicketEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val ticketCode: String, // e.g. "T-101" or "#001"
    val citizenName: String,
    val phoneNumber: String,
    val category: String, // "Personal" or "Issue"
    val subCategory: String, // e.g. "Medical Aid", "Street Light"
    val description: String,
    val location: String, // e.g. "Ward 12, Market Area"
    val assignedPa: String, // latest assigned PA
    val isSolved: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val solvedAt: Long? = null,
    val resolutionNotes: String? = null,
    val priority: String = "Medium",
    val reportsJson: String = "", // Serialized list of TicketReportEntry
    val lastViewedAt: Long = 0L,
    val reopenedNotice: String? = null
) {
    /**
     * Normalized ticket number for exact matching: "T-101" and "t-101 " match.
     */
    val normalizedTicketNumber: String
        get() = ticketCode.uppercase().trim().replace("#", "")

    /**
     * Ticket Status: "Open", "In Progress", or "Closed"
     */
    val statusText: String
        get() = if (isSolved) "Closed" else "Open"

    /**
     * Complete list of all individual report & issue submissions for this ticket.
     * Guaranteed to never be empty.
     */
    val reports: List<TicketReportEntry>
        get() {
            val parsed = TicketReportJsonHelper.fromJson(reportsJson)
            return if (parsed.isNotEmpty()) {
                var currentNum = 1
                parsed.map { entry ->
                    if (entry.issueNumber > 0) {
                        currentNum = maxOf(currentNum, entry.issueNumber + 1)
                        entry
                    } else {
                        val assigned = entry.copy(issueNumber = currentNum)
                        currentNum++
                        assigned
                    }
                }
            } else {
                listOf(
                    TicketReportEntry(
                        paName = assignedPa.takeIf { it.isNotBlank() && it != "PA Assigned" } ?: "PA",
                        category = category.ifBlank { "Issue" },
                        subject = subCategory.ifBlank { "General Request" },
                        description = description,
                        timestamp = createdAt,
                        entryType = "ISSUE",
                        issueNumber = 1
                    )
                )
            }
        }

    val issuesList: List<TicketReportEntry>
        get() = reports.filter { it.isIssue }

    val reportsListOnly: List<TicketReportEntry>
        get() = reports.filter { !it.isIssue }

    val issueCount: Int
        get() = issuesList.size.coerceAtLeast(if (description.isNotBlank()) 1 else 0)

    val reportCount: Int
        get() = visibleReports.size

    val hasUnreadUpdate: Boolean
        get() = lastViewedAt > 0L && latestTimestamp > lastViewedAt

    val ticketAddress: String
        get() {
            if (location.isNotBlank()) return location
            if (description.startsWith("Address:", ignoreCase = true)) {
                return description.trim()
            }
            return ""
        }

    val effectiveAddress: String
        get() {
            val addr = ticketAddress
            if (addr.isNotBlank()) {
                return if (addr.startsWith("Address:", ignoreCase = true)) {
                    addr.replaceFirst(Regex("^\\s*Address\\s*:", RegexOption.IGNORE_CASE), "").trim()
                } else {
                    addr
                }
            }
            return ""
        }

    val ticketIssue: String
        get() {
            if (location.isBlank() && description.startsWith("Address:", ignoreCase = true)) {
                return ""
            }
            return description
        }

    val effectiveDescription: String
        get() {
            val issue = ticketIssue
            val finalIssue = if (issue.isBlank()) {
                visibleReports.firstOrNull()?.description ?: ""
            } else {
                issue
            }
            return if (finalIssue.isBlank() || finalIssue.startsWith("Address:", ignoreCase = true)) {
                "No issue written"
            } else {
                finalIssue
            }
        }

    val visibleReports: List<TicketReportEntry>
        get() {
            val seen = mutableSetOf<String>()
            // Requirement 3: Sort entries in ascending order by issue number
            val sorted = reports.sortedWith(
                compareBy({ if (it.issueNumber > 0) it.issueNumber else Int.MAX_VALUE }, { it.timestamp })
            )
            val list = mutableListOf<TicketReportEntry>()
            for (r in sorted) {
                val txt = r.description.ifBlank { r.subject }.trim()
                if (txt.isNotBlank() && !txt.startsWith("Address:", ignoreCase = true)) {
                    val key = "${r.issueNumber}:${txt.lowercase()}"
                    if (!seen.contains(key)) {
                        seen.add(key)
                        list.add(r)
                    }
                }
            }
            return list
        }

    val latestTimestamp: Long
        get() = reports.maxOfOrNull { it.timestamp } ?: createdAt

    /**
     * Reflects the date and time of the latest report entry.
     */
    val formattedDateTime: String
        get() {
            val sdf = SimpleDateFormat("dd-MM-yyyy, hh:mm a", Locale.ENGLISH)
            return sdf.format(Date(latestTimestamp))
        }

    /**
     * Unique categories present across all submissions in this ticket (e.g., ["Personal", "Issue"]).
     */
    val distinctCategories: List<String>
        get() = reports.map { it.category }.distinct().filter { it.isNotBlank() }.ifEmpty { listOf(category) }

    /**
     * Name of the PA who created or submitted the report for this ticket.
     * Extracts the real PA name, ignoring generic placeholders.
     */
    val addedByPaName: String
        get() {
            val isPlaceholder = { s: String? ->
                if (s.isNullOrBlank()) true
                else {
                    val l = s.trim().lowercase()
                    l == "pa" || l == "pa assigned" || l == "paassigned" || l == "assigned" ||
                    l == "unassigned" || l == "n/a" || l == "na" || l == "null" || l == "none" ||
                    l == "pending" || l == "default" || l == "unknown"
                }
            }

            // 1. Check reports for real PA name
            val fromReports = reports.map { it.paName.trim() }.firstOrNull { !isPlaceholder(it) }
            if (!fromReports.isNullOrBlank()) return fromReports

            // 2. Check assignedPa field
            val pa = assignedPa.trim()
            if (!isPlaceholder(pa)) return pa

            // 3. Fallback to any non-blank value if nothing else
            val anyReport = reports.map { it.paName.trim() }.firstOrNull { it.isNotBlank() }
            if (!anyReport.isNullOrBlank()) return anyReport

            return if (pa.isNotBlank()) pa else "PA"
        }
}

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val ticketId: Int,
    val senderName: String, // "Leader Jeevan", "Citizen", "PA Ramesh"
    val isLeader: Boolean,
    val message: String,
    val timestamp: Long = System.currentTimeMillis()
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("dd-MM-yyyy, hh:mm a", Locale.ENGLISH)
            return sdf.format(Date(timestamp))
        }
}

@Entity(tableName = "timeline_events")
data class TimelineEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val ticketId: Int,
    val title: String,
    val description: String,
    val actor: String, // "Leader Jeevan", "Citizen", "System"
    val timestamp: Long = System.currentTimeMillis()
) {
    val formattedTime: String
        get() {
            val sdf = SimpleDateFormat("dd-MM-yyyy, hh:mm a", Locale.ENGLISH)
            return sdf.format(Date(timestamp))
        }
}

@Entity(tableName = "pa_users")
data class PaUserEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val paName: String, // e.g. "PA Ramesh"
    val phoneNumber: String, // e.g. "9876543210"
    val password: String, // e.g. "ramesh123"
    val designation: String = "Personal Assistant",
    val status: String = "Active", // "Active", "Inactive"
    val createdAt: Long = System.currentTimeMillis(),
    val firestoreDocId: String? = null
) {
    val formattedCreatedDate: String
        get() {
            val sdf = SimpleDateFormat("dd-MM-yyyy, hh:mm a", Locale.ENGLISH)
            return sdf.format(Date(createdAt))
        }
}
