package com.example.data.remote

import android.util.Log
import com.example.data.local.TicketDao
import com.example.data.model.PaUserEntity
import com.example.data.model.TicketEntity
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap

/**
 * Maps Firestore documents from the ticket portal into TicketEntity.
 * Robustly extracts the original submitter name from `submitterName` (highest priority),
 * ensuring the original citizen/submitter who raised the ticket is shown in the Leader Portal,
 * and extracts the assigned PA name accurately without confusing it with the submitter.
 */
object FirestoreTicketMapper {
    private const val TAG = "FirestoreTicketMapper"

    /**
     * Extracts the real citizen/submitter name with top priority given to submitterName.
     */
    fun extractCitizenName(data: Map<String, Any?>): String? {
        val allMaps = mutableListOf<Map<String, Any?>>()
        allMaps.add(data)

        // Include any nested maps (such as submitter, citizen, user, applicant, formData, data, ticket, details, profile)
        for ((_, v) in data) {
            if (v is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                allMaps.add(v as Map<String, Any?>)
            }
        }

        // Priority candidate normalized keys - SUBMITTER / CITIZEN NAME is #1 priority
        val priorityNormalized = listOf(
            "submittername",
            "submitterfullname",
            "citizenname",
            "applicantname",
            "clientname",
            "callername",
            "personname",
            "contactname",
            "beneficiaryname",
            "fullname",
            "submitter",
            "submittedby",
            "displayname",
            "username",
            "name"
        )

        for (map in allMaps) {
            val normalizedEntries = map.entries.mapNotNull { entry ->
                val normKey = entry.key.lowercase().replace(" ", "").replace("_", "").replace("-", "")
                val strVal = when (val value = entry.value) {
                    is String -> value.trim().takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    else -> null
                }
                if (strVal != null) normKey to strVal else null
            }.toMap()

            // 1. Check exact priority normalized keys: submitterName first!
            for (candidate in priorityNormalized) {
                normalizedEntries[candidate]?.let { return it }
            }

            // 2. Check if both firstname and lastname exist
            val firstName = normalizedEntries["firstname"]
            val lastName = normalizedEntries["lastname"]
            if (firstName != null) {
                return if (lastName != null) "$firstName $lastName" else firstName
            }

            // 3. Check entity scalar keys: "submitter", "citizen", "applicant", "client", "caller", "person", "user"
            val entityKeys = listOf("submitter", "citizen", "applicant", "client", "caller", "person", "user")
            for (ek in entityKeys) {
                val candidateVal = normalizedEntries[ek]
                if (candidateVal != null && candidateVal.length in 2..50 && !candidateVal.contains("{")) {
                    return candidateVal
                }
            }

            // 4. Check any key ending with "name" (excluding file, class, app, pa, created, added)
            for ((key, value) in normalizedEntries) {
                if (key.endsWith("name") && !key.contains("file") && !key.contains("class") && !key.contains("app") && !key.contains("pa") && !key.contains("added") && !key.contains("created")) {
                    return value
                }
            }

            // 5. Check any key containing "submitter" or "citizen" whose value is a string
            for ((key, value) in normalizedEntries) {
                if ((key.contains("submitter") || key.contains("citizen")) && value.length in 2..50 && !value.contains("{")) {
                    return value
                }
            }
        }

        return null
    }

    /**
     * Extracts the real PA name (who created/added the ticket) from the Firestore document.
     * Prioritizes addedBy, addedByPa, createdBy, paName, assignedPa, author, creator across all schema variants,
     * including nested maps, report lists, and phone/ID lookup against real PA user accounts.
     */
    fun extractPaName(
        data: Map<String, Any?>,
        citizenName: String,
        paLookup: ((String) -> String?)? = null
    ): String {
        val isInvalidPlaceholder = { s: String? ->
            if (s.isNullOrBlank()) true
            else {
                val l = s.trim().lowercase()
                l == "pa" || l == "pa assigned" || l == "paassigned" || l == "assigned" ||
                l == "unassigned" || l == "n/a" || l == "na" || l == "null" || l == "none" ||
                l == "pending" || l == "default" || l == "unknown" || l == "ticket" ||
                l.startsWith("address:", ignoreCase = true)
            }
        }

        val resolveIfPhoneOrId = { raw: String ->
            val trimmed = raw.trim()
            if (paLookup != null) {
                val lookupResult = paLookup(trimmed)
                if (!lookupResult.isNullOrBlank() && !isInvalidPlaceholder(lookupResult)) {
                    lookupResult.trim()
                } else {
                    val digits = trimmed.replace(Regex("[^0-9]"), "")
                    if (digits.length >= 7) {
                        val digitLookup = paLookup(digits)
                        if (!digitLookup.isNullOrBlank() && !isInvalidPlaceholder(digitLookup)) {
                            digitLookup.trim()
                        } else null
                    } else null
                }
            } else null
        }

        // 1. Check nested objects OR direct string values specifically under addedBy, createdBy, pa, assignedTo, user, etc.
        val objectKeys = listOf(
            "addedBy", "added_by", "addedByPa", "added_by_pa",
            "createdBy", "created_by", "createdByPa", "created_by_pa",
            "pa", "paDetails", "pa_details", "paInfo", "pa_info", "paUser", "pa_user",
            "submittedBy", "submitted_by", "submittedByPa",
            "raisedBy", "raised_by", "reportedBy", "reported_by",
            "author", "authorDetails", "creator", "creatorDetails",
            "assignedTo", "assigned_to", "assignedPa", "assigned_pa",
            "user", "userInfo", "user_info", "userDetails", "user_details",
            "officer", "staff", "agent", "operator"
        )
        val nameSubKeys = listOf(
            "name", "paName", "pa_name", "realName", "real_name", "paRealName",
            "displayName", "display_name", "fullName", "full_name",
            "userName", "username", "user_name", "firstName", "first_name",
            "addedByName", "createdByName", "submittedByName",
            "phone", "phoneNumber", "mobile", "contact", "id", "uid"
        )

        for (ok in objectKeys) {
            val nested = data[ok]
            if (nested is Map<*, *>) {
                for (nk in nameSubKeys) {
                    val v = nested[nk]?.toString()?.trim()
                    if (!v.isNullOrBlank()) {
                        val resolved = resolveIfPhoneOrId(v)
                        if (resolved != null) return resolved
                        if (!isInvalidPlaceholder(v)) return v
                    }
                }
            } else if (nested is String) {
                val v = nested.trim()
                if (v.isNotBlank()) {
                    val resolved = resolveIfPhoneOrId(v)
                    if (resolved != null) return resolved
                    if (!isInvalidPlaceholder(v)) return v
                }
            }
        }

        // 2. Check reports / submissions / entries list
        val reportsList = data["reports"] ?: data["submissions"] ?: data["entries"] ?: data["history"]
        if (reportsList is List<*>) {
            for (item in reportsList) {
                if (item is Map<*, *>) {
                    val reportPa = item["paName"] ?: item["addedBy"] ?: item["createdBy"] ?: item["pa"]
                        ?: item["realPaName"] ?: item["name"] ?: item["officer"] ?: item["author"]
                    val reportPaStr = reportPa?.toString()?.trim()
                    if (!reportPaStr.isNullOrBlank()) {
                        val resolved = resolveIfPhoneOrId(reportPaStr)
                        if (resolved != null) return resolved
                        if (!isInvalidPlaceholder(reportPaStr)) {
                            return reportPaStr
                        }
                    }
                }
            }
        }

        // 3. Collect all nested maps recursively
        val allMaps = mutableListOf<Map<String, Any?>>()
        fun collectMaps(m: Map<*, *>) {
            @Suppress("UNCHECKED_CAST")
            allMaps.add(m as Map<String, Any?>)
            for ((_, v) in m) {
                if (v is Map<*, *>) {
                    collectMaps(v)
                }
            }
        }
        collectMaps(data)

        val paKeys = listOf(
            "realpaname", "parealname",
            "addedbypaname", "addedbypa", "addedbyname", "addedby",
            "createdbypaname", "createdbypa", "createdbyname", "createdby",
            "paname", "pa_name", "pa",
            "submittedbypaname", "submittedbypa", "submittedbyname", "submittedby",
            "raisedbypaname", "raisedbypa", "raisedbyname", "raisedby",
            "reportedbypaname", "reportedbypa", "reportedbyname", "reportedby",
            "authorname", "author", "creatorname", "creator",
            "pausername", "pauser", "paincharge", "inchargepa", "incharge",
            "officername", "officer", "staffname", "staff", "agentname", "agent",
            "operatorname", "operator", "assignedpaname", "assignedpa", "assignedto",
            "enteredby", "loggedby",
            "paphone", "pa_phone", "pamobile", "pa_mobile", "addedbyphone", "createdbyphone",
            "paid", "pa_id"
        )

        for (map in allMaps) {
            val normalizedEntries = map.entries.mapNotNull { entry ->
                val normKey = entry.key.lowercase().replace(" ", "").replace("_", "").replace("-", "")
                val strVal = when (val value = entry.value) {
                    is String -> value.trim().takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    is Number -> value.toLong().toString()
                    else -> null
                }
                if (strVal != null) normKey to strVal else null
            }.toMap()

            for (candidate in paKeys) {
                val value = normalizedEntries[candidate]
                if (value != null) {
                    val resolved = resolveIfPhoneOrId(value)
                    if (resolved != null) return resolved
                    if (!isInvalidPlaceholder(value)) {
                        return value
                    }
                }
            }

            // If a separate "name" exists and it's valid, check if it resolves or serves as PA name
            val nameField = normalizedEntries["name"]
            if (nameField != null) {
                val resolved = resolveIfPhoneOrId(nameField)
                if (resolved != null) return resolved
            }
        }

        // 4. Scan for any value starting with "PA " or "PA-" or "PA:"
        for (map in allMaps) {
            for ((_, value) in map) {
                val str = value?.toString()?.trim() ?: continue
                if (str.isNotBlank() && !isInvalidPlaceholder(str)) {
                    if (str.startsWith("PA ", ignoreCase = true) ||
                        str.startsWith("PA-", ignoreCase = true) ||
                        str.startsWith("PA:", ignoreCase = true)
                    ) {
                        return str
                    }
                }
            }
        }

        return "PA"
    }

    /**
     * Recursively and case-insensitively extracts the phone number
     * from any document schema or nested structure.
     */
    fun extractPhoneNumber(data: Map<String, Any?>): String {
        val allMaps = mutableListOf<Map<String, Any?>>()
        allMaps.add(data)
        for ((_, v) in data) {
            if (v is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                allMaps.add(v as Map<String, Any?>)
            }
        }

        val phoneKeys = listOf(
            "submitterphone", "submittermobile", "submittercontact",
            "phonenumber", "mobilenumber", "contactnumber",
            "phone", "mobile", "contact", "cell", "cellphone",
            "telephone", "whatsapp", "citizenphone", "userphone",
            "applicantphone", "callerphone"
        )

        for (map in allMaps) {
            val normalizedEntries = map.entries.mapNotNull { entry ->
                val normKey = entry.key.lowercase().replace(" ", "").replace("_", "").replace("-", "")
                val strVal = when (val value = entry.value) {
                    is String -> value.trim().takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                    is Number -> value.toLong().toString()
                    else -> null
                }
                if (strVal != null) normKey to strVal else null
            }.toMap()

            for (candidate in phoneKeys) {
                normalizedEntries[candidate]?.let { return it }
            }

            for ((key, value) in normalizedEntries) {
                if ((key.contains("phone") || key.contains("mobile") || key.contains("contact")) && value.length >= 7) {
                    return value
                }
            }
        }

        return ""
    }

    fun extractString(data: Map<String, Any?>, keys: List<String>): String? {
        for (k in keys) {
            val v = data[k] ?: continue
            if (v is Map<*, *> || v is Collection<*>) continue
            when (v) {
                is String -> {
                    val s = v.trim()
                    if (s.isNotBlank() && !s.equals("null", ignoreCase = true)) return s
                }
                is Number -> return v.toLong().toString()
                else -> {
                    val s = v.toString().trim()
                    if (s.isNotBlank() && !s.equals("null", ignoreCase = true)) return s
                }
            }
        }
        return null
    }

    /**
     * Extracts a PaUserEntity from a Firestore document, supporting multiple schema variations.
     */
    fun extractPaUserFromDocument(doc: DocumentSnapshot): PaUserEntity? {
        val data = doc.data ?: return null

        val allMaps = mutableListOf<Map<String, Any?>>()
        allMaps.add(data)
        for ((_, v) in data) {
            if (v is Map<*, *>) {
                @Suppress("UNCHECKED_CAST")
                allMaps.add(v as Map<String, Any?>)
            }
        }

        var paName: String? = null
        var phone: String? = null
        var password: String? = null
        var designation: String? = null
        var status: String? = null
        var role: String? = null

        for (map in allMaps) {
            if (paName == null) {
                paName = extractString(
                    map,
                    listOf("paName", "pa_name", "realName", "real_name", "paRealName", "pa_real_name", "name", "username", "user_name", "displayName", "display_name", "fullname", "full_name", "title", "addedByName", "createdByName")
                )
            }
            if (phone == null) {
                phone = extractString(
                    map,
                    listOf("phoneNumber", "phone_number", "phone", "mobile", "contact", "user_phone", "mobileNumber")
                )
            }
            if (password == null) {
                password = extractString(
                    map,
                    listOf("password", "pass", "pwd", "pin", "code")
                )
            }
            if (designation == null) {
                designation = extractString(
                    map,
                    listOf("designation", "role", "title", "job_title", "position")
                )
            }
            if (status == null) {
                status = extractString(
                    map,
                    listOf("status", "state", "active")
                )
            }
            if (role == null) {
                role = extractString(
                    map,
                    listOf("role", "type", "userType", "user_type")
                )
            }
        }

        // For generic "users" collection, ignore if role is strictly citizen or public
        if (doc.reference.parent.id == "users") {
            val roleLower = role?.lowercase() ?: ""
            if (!roleLower.contains("pa") && !roleLower.contains("assistant") && !roleLower.contains("staff") && !roleLower.contains("admin")) {
                if (roleLower.contains("citizen") || roleLower.contains("public") || roleLower.contains("user")) {
                    return null
                }
            }
        }

        if (paName.isNullOrBlank() && phone.isNullOrBlank()) {
            return null
        }

        val finalName = when {
            !paName.isNullOrBlank() -> paName
            !phone.isNullOrBlank() -> "PA ($phone)"
            else -> "PA User"
        }

        val finalPhone = phone ?: ""
        val finalPassword = password ?: "pa_pass123"
        val finalDesignation = designation ?: "Personal Assistant"
        val isInactive = status?.equals("false", ignoreCase = true) == true ||
                status?.equals("inactive", ignoreCase = true) == true ||
                status?.equals("disabled", ignoreCase = true) == true
        val finalStatus = if (isInactive) "Inactive" else "Active"

        val createdAt = when (val ts = data["createdAt"] ?: data["created_at"] ?: data["timestamp"] ?: data["updatedAt"]) {
            is Long -> ts
            is Timestamp -> ts.toDate().time
            is Double -> ts.toLong()
            else -> System.currentTimeMillis()
        }

        return PaUserEntity(
            paName = finalName,
            phoneNumber = finalPhone,
            password = finalPassword,
            designation = finalDesignation,
            status = finalStatus,
            createdAt = createdAt,
            firestoreDocId = doc.id
        )
    }

    fun fromMap(data: Map<String, Any?>, fallbackId: String, paLookup: ((String) -> String?)? = null): TicketEntity {
        // Ticket Code extraction
        val rawCode = extractString(
            data,
            listOf("ticketCode", "ticket_code", "ticketId", "ticket_id", "ticketNumber", "ticket_number", "code", "id")
        ) ?: fallbackId

        val formattedCode = when {
            rawCode.startsWith("#") -> rawCode
            rawCode.isNotBlank() -> "#$rawCode"
            else -> "#${fallbackId.ifBlank { "001" }}"
        }

        // Submitter / Citizen Name extraction - prioritized from submitterName
        val citizenName = extractCitizenName(data)
            ?: extractPhoneNumber(data).takeIf { it.isNotBlank() }?.let { "Citizen ($it)" }
            ?: fallbackId.takeIf { it.isNotBlank() }?.let { "Ticket #$it" }
            ?: "Constituent"

        // Assigned PA extraction
        val assignedPa = extractPaName(data, citizenName, paLookup)

        // Phone Number extraction
        val rawPhone = extractPhoneNumber(data)

        // Category: "Personal" or "Issue"
        val rawCategory = extractString(
            data,
            listOf("category", "type", "ticketType", "ticket_type", "issueCategory", "requestType")
        ) ?: "Issue"
        val category = if (rawCategory.contains("Personal", ignoreCase = true)) "Personal" else "Issue"

        // SubCategory / Title
        val subCategory = extractString(
            data,
            listOf("subCategory", "subcategory", "sub_category", "title", "subject", "issueType", "problem", "department", "topic")
        ) ?: "General Request"

        // Description
        val description = extractString(
            data,
            listOf("description", "details", "desc", "message", "problemDescription", "issue", "content", "note", "body")
        ) ?: ""

        // Location / Address from Firestore database
        val location = extractString(
            data,
            listOf("address", "location", "area", "ward", "colony", "city", "village", "place")
        ) ?: ""

        // Status: Solved vs Unsolved
        val isSolved = when (val sVal = data["isSolved"] ?: data["solved"]) {
            is Boolean -> sVal
            else -> {
                val statusStr = extractString(data, listOf("status", "ticketStatus", "ticket_status", "state")) ?: ""
                statusStr.equals("Solved", ignoreCase = true) ||
                        statusStr.equals("Resolved", ignoreCase = true) ||
                        statusStr.equals("Closed", ignoreCase = true) ||
                        statusStr.equals("Completed", ignoreCase = true)
            }
        }

        // Timestamps
        val createdAt = when (val c = data["createdAt"] ?: data["timestamp"] ?: data["created_at"] ?: data["date"]) {
            is Long -> c
            is Number -> c.toLong()
            is Timestamp -> c.toDate().time
            else -> System.currentTimeMillis()
        }

        val solvedAt = when (val s = data["solvedAt"] ?: data["resolvedAt"] ?: data["closedAt"]) {
            is Long -> s
            is Number -> s.toLong()
            is Timestamp -> s.toDate().time
            else -> if (isSolved) System.currentTimeMillis() else null
        }

        val resolutionNotes = extractString(
            data,
            listOf("resolutionNotes", "resolution_notes", "notes", "remarks", "solution", "actionTaken")
        )

        val priority = extractString(
            data,
            listOf("priority", "urgency", "level")
        ) ?: "Medium"

        val reportsJsonFromMap = when (val r = data["reportsJson"]) {
            is String -> r.takeIf { it.isNotBlank() }
            else -> null
        }
        val reportsListJson = if (reportsJsonFromMap != null) {
            reportsJsonFromMap
        } else when (val rList = data["reports"] ?: data["issues"] ?: data["submissions"] ?: data["entries"] ?: data["history"]) {
            is List<*> -> {
                val entries = mutableListOf<com.example.data.model.TicketReportEntry>()
                for (item in rList) {
                    if (item is Map<*, *>) {
                        val pa = item["paName"]?.toString()
                            ?: item["raised_by"]?.toString()
                            ?: item["submitted_by"]?.toString()
                            ?: item["addedBy"]?.toString()
                            ?: item["createdBy"]?.toString()
                            ?: item["pa"]?.toString()
                            ?: item["officer"]?.toString()
                            ?: assignedPa
                        val cat = item["category"]?.toString() ?: category
                        val sub = item["subject"]?.toString() ?: subCategory
                        val desc = item["description"]?.toString()
                            ?: item["details"]?.toString()
                            ?: item["content"]?.toString()
                            ?: description
                        val entryType = item["entryType"]?.toString()
                            ?: if (cat.contains("Personal", ignoreCase = true)) "REPORT" else "ISSUE"
                        val ts = when (val t = item["timestamp"] ?: item["raised_at"] ?: item["submitted_at"]) {
                            is Number -> t.toLong()
                            is Timestamp -> t.toDate().time
                            else -> createdAt
                        }
                        val issueNum = (item["issueNumber"] ?: item["issue_number"] ?: item["serialNumber"])?.toString()?.toIntOrNull() ?: 0
                        entries.add(
                            com.example.data.model.TicketReportEntry(
                                paName = pa,
                                category = cat,
                                subject = sub,
                                description = desc,
                                timestamp = ts,
                                entryType = entryType,
                                issueNumber = issueNum
                            )
                        )
                    }
                }
                if (entries.isNotEmpty()) com.example.data.model.TicketReportJsonHelper.toJson(entries) else ""
            }
            else -> ""
        }

        return TicketEntity(
            ticketCode = formattedCode,
            citizenName = citizenName,
            phoneNumber = rawPhone,
            category = category,
            subCategory = subCategory,
            description = description,
            location = location,
            assignedPa = assignedPa,
            isSolved = isSolved,
            createdAt = createdAt,
            solvedAt = solvedAt,
            resolutionNotes = resolutionNotes,
            priority = priority,
            reportsJson = reportsListJson
        )
    }

    fun fromDocument(doc: DocumentSnapshot, paLookup: ((String) -> String?)? = null): TicketEntity? {
        val data = doc.data ?: return null
        return fromMap(data, doc.id, paLookup)
    }

    fun toMap(ticket: TicketEntity): Map<String, Any?> {
        val reportsData = ticket.reports.map { r ->
            mapOf(
                "id" to r.id,
                "paName" to r.paName,
                "category" to r.category,
                "subject" to r.subject,
                "description" to r.description,
                "timestamp" to r.timestamp,
                "entryType" to r.entryType,
                "issueNumber" to r.issueNumber,
                "issue_number" to r.issueNumber
            )
        }

        val issuesData = ticket.reports.map { r ->
            mapOf(
                "issue_id" to r.id,
                "id" to r.id,
                "description" to r.description,
                "raised_by" to r.paName,
                "paName" to r.paName,
                "raised_at" to r.timestamp,
                "timestamp" to r.timestamp,
                "category" to r.category,
                "subject" to r.subject,
                "status" to if (ticket.isSolved) "Closed" else "Open",
                "entryType" to r.entryType,
                "issueNumber" to r.issueNumber,
                "issue_number" to r.issueNumber
            )
        }

        val cleanPhone = ticket.phoneNumber.replace(Regex("[^0-9]"), "")

        return hashMapOf(
            "ticketCode" to ticket.ticketCode,
            "ticket_code" to ticket.ticketCode,
            "ticketNumber" to ticket.ticketCode,
            "ticket_number" to ticket.ticketCode,
            "citizenName" to ticket.citizenName,
            "submitterName" to ticket.citizenName,
            "phoneNumber" to ticket.phoneNumber,
            "phone" to ticket.phoneNumber,
            "mobile" to ticket.phoneNumber,
            "cleanPhone" to cleanPhone,
            "category" to ticket.category,
            "subCategory" to ticket.subCategory,
            "description" to ticket.description,
            "address" to ticket.location,
            "location" to ticket.location,
            "addedBy" to ticket.addedByPaName,
            "addedByPa" to ticket.addedByPaName,
            "paName" to ticket.addedByPaName,
            "assignedPa" to ticket.assignedPa,
            "isSolved" to ticket.isSolved,
            "status" to if (ticket.isSolved) "Solved" else "Open",
            "createdAt" to ticket.createdAt,
            "created_at" to ticket.createdAt,
            "updated_at" to System.currentTimeMillis(),
            "solvedAt" to ticket.solvedAt,
            "resolutionNotes" to ticket.resolutionNotes,
            "priority" to ticket.priority,
            "reportsJson" to ticket.reportsJson,
            "reports" to reportsData,
            "issues" to issuesData,
            "reportCount" to ticket.reportCount
        )
    }
}

/**
 * Real-time synchronization service connecting Firestore collections
 * to the local Room database, ensuring whatever details are raised in the database
 * appear with the exact submitter name and phone number in both table overview and activity view.
 */
class FirestoreTicketSyncService(
    private val ticketDao: TicketDao,
    private val firestore: FirebaseFirestore? = try {
        FirebaseFirestore.getInstance()
    } catch (e: Exception) {
        null
    },
    private val coroutineScope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {
    private val listeners = mutableListOf<ListenerRegistration>()
    private val paSyncMutex = Mutex()
    var onLeaderCredentialUpdate: ((name: String, phone: String, pass: String, designation: String, jurisdiction: String) -> Unit)? = null
    private val sampleNames = listOf(
        "Ravi Teja",
        "Anuradha Rao",
        "K. Suresh Reddy",
        "B. Lakshmi Devi",
        "Venkatesh Naidu"
    )
    // Tracks active document IDs per Firestore collection to detect deleted tickets during reconciliation
    private val collectionDocIds = ConcurrentHashMap<String, MutableSet<String>>()
    val knownPaNameByPhoneOrId = ConcurrentHashMap<String, String>()

    init {
        coroutineScope.launch {
            try {
                ticketDao.getAllPaUsersList().forEach { registerKnownPa(it) }
            } catch (_: Exception) {}
        }
    }

    fun resolvePaName(key: String): String? {
        val clean = key.trim()
        if (clean.isBlank()) return null
        val digits = clean.replace(Regex("[^0-9]"), "")
        val norm = clean.lowercase().replace(" ", "").replace("_", "").replace("-", "")

        return knownPaNameByPhoneOrId[clean]
            ?: knownPaNameByPhoneOrId[clean.lowercase()]
            ?: knownPaNameByPhoneOrId[norm]
            ?: if (digits.isNotBlank()) knownPaNameByPhoneOrId[digits] else null
            ?: if (digits.length >= 7) knownPaNameByPhoneOrId[digits.takeLast(10)] else null
    }

    fun registerKnownPa(pa: PaUserEntity) {
        val name = pa.paName.trim()
        val isInvalid = name.isBlank() || name.equals("PA", ignoreCase = true) ||
                name.equals("PA Assigned", ignoreCase = true) || name.equals("PA User", ignoreCase = true)
        if (!isInvalid) {
            val rawPhone = pa.phoneNumber.trim()
            val cleanPhone = rawPhone.replace(Regex("[^0-9]"), "")
            if (rawPhone.isNotBlank()) {
                knownPaNameByPhoneOrId[rawPhone] = name
                knownPaNameByPhoneOrId[rawPhone.lowercase()] = name
            }
            if (cleanPhone.isNotBlank()) {
                knownPaNameByPhoneOrId[cleanPhone] = name
                if (cleanPhone.length >= 7) knownPaNameByPhoneOrId[cleanPhone.takeLast(10)] = name
            }
            if (!pa.firestoreDocId.isNullOrBlank()) {
                knownPaNameByPhoneOrId[pa.firestoreDocId!!] = name
                knownPaNameByPhoneOrId[pa.firestoreDocId!!.lowercase()] = name
            }
            if (pa.id > 0) knownPaNameByPhoneOrId[pa.id.toString()] = name

            val normName = name.lowercase().replace(" ", "").replace("_", "").replace("-", "")
            knownPaNameByPhoneOrId[normName] = name

            // Re-check and update any existing tickets whose assignedPa is this phone number, ID, or placeholder
            coroutineScope.launch {
                try {
                    val allTickets = ticketDao.getAllTicketsSync()
                    for (t in allTickets) {
                        val tPa = t.assignedPa.trim()
                        val isPaPlaceholder = tPa.isBlank() || tPa.equals("PA", ignoreCase = true) ||
                                tPa.equals("PA Assigned", ignoreCase = true)
                        val tDigits = tPa.replace(Regex("[^0-9]"), "")
                        val isPhoneMatch = (cleanPhone.isNotBlank() && tDigits.isNotBlank() && tDigits.endsWith(cleanPhone.takeLast(10))) ||
                                (rawPhone.isNotBlank() && tPa.equals(rawPhone, ignoreCase = true)) ||
                                (!pa.firestoreDocId.isNullOrBlank() && tPa.equals(pa.firestoreDocId, ignoreCase = true))
                        if (isPhoneMatch || (isPaPlaceholder && t.addedByPaName.equals("PA", ignoreCase = true))) {
                            val updatedReports = t.reports.map { r ->
                                val rPa = r.paName.trim()
                                val rDigits = rPa.replace(Regex("[^0-9]"), "")
                                if (rPa.isBlank() || rPa.equals("PA", ignoreCase = true) || rPa.equals("PA Assigned", ignoreCase = true) ||
                                    (cleanPhone.isNotBlank() && rDigits.isNotBlank() && rDigits.endsWith(cleanPhone.takeLast(10))) ||
                                    (rawPhone.isNotBlank() && rPa.equals(rawPhone, ignoreCase = true))
                                ) {
                                    r.copy(paName = name)
                                } else r
                            }
                            val updatedJson = com.example.data.model.TicketReportJsonHelper.toJson(updatedReports)
                            ticketDao.updateTicket(t.copy(assignedPa = name, reportsJson = updatedJson))
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun startListening() {
        val db = firestore ?: run {
            Log.w("FirestoreTicketSync", "Firestore instance not available, skipping real-time listener.")
            return
        }

        stopListening()

        val collectionsToListen = listOf(
            "tickets",
            "Tickets",
            "grievances",
            "Grievances",
            "requests",
            "Requests",
            "issues",
            "Issues",
            "complaints",
            "Complaints",
            "citizen_tickets",
            "pa_tickets"
        )

        for (colName in collectionsToListen) {
            try {
                val registration = db.collection(colName)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            Log.e("FirestoreTicketSync", "Error listening to collection '$colName'", error)
                            return@addSnapshotListener
                        }

                        if (snapshot == null) return@addSnapshotListener

                        coroutineScope.launch {
                            // 1. Remove sample dummy tickets if any real data exists
                            if (!snapshot.isEmpty) {
                                ticketDao.deleteTicketsByCitizenNames(sampleNames)
                            }

                            // 2. Real-time Auto-Sync for DELETED documents in Firestore:
                            // When a ticket is deleted in Firestore / ticket portal, delete it immediately from the Leader Portal
                            for (change in snapshot.documentChanges) {
                                if (change.type == DocumentChange.Type.REMOVED) {
                                    val removedDoc = change.document
                                    deleteTicketByDoc(removedDoc)
                                }
                            }

                            // 3. Collection document set reconciliation (detects deletions even across offline/reconnects)
                            val currentIds = snapshot.documents.map { it.id }.toSet()
                            val previousIds = collectionDocIds[colName]
                            if (previousIds != null) {
                                val removedIds = previousIds - currentIds
                                for (removedId in removedIds) {
                                    deleteTicketByIdOrCode(removedId)
                                }
                            }
                            collectionDocIds[colName] = currentIds.toMutableSet()

                            // 4. Auto-Sync for ADDED and MODIFIED documents:
                            for (change in snapshot.documentChanges) {
                                if (change.type == DocumentChange.Type.ADDED ||
                                    change.type == DocumentChange.Type.MODIFIED
                                ) {
                                    val doc = change.document
                                    val remoteTicket = FirestoreTicketMapper.fromDocument(doc, ::resolvePaName) ?: continue

                                    val digitsRemote = remoteTicket.phoneNumber.replace(Regex("[^0-9]"), "").trim()
                                    val cleanRemotePhone = if (digitsRemote.length >= 10) digitsRemote.takeLast(10) else digitsRemote
                                    val normRemoteCode = remoteTicket.ticketCode.trim().uppercase().replace("#", "")

                                    val allLocal = ticketDao.getAllTicketsSync()

                                    // Find local ticket by code OR by phone number (10 digits clean)
                                    val localTicket = allLocal.firstOrNull { t ->
                                        val tCode = t.ticketCode.trim().uppercase().replace("#", "")
                                        val codeMatch = normRemoteCode.isNotBlank() && (
                                            tCode == normRemoteCode ||
                                            tCode == "T-$normRemoteCode" ||
                                            "T-$tCode" == normRemoteCode
                                        )
                                        val digitsLocal = t.phoneNumber.replace(Regex("[^0-9]"), "").trim()
                                        val cleanLocalPhone = if (digitsLocal.length >= 10) digitsLocal.takeLast(10) else digitsLocal
                                        val phoneMatch = cleanRemotePhone.isNotBlank() && cleanRemotePhone.length >= 7 && cleanLocalPhone == cleanRemotePhone

                                        codeMatch || phoneMatch
                                    }

                                    if (localTicket != null) {
                                        val effectivePa = when {
                                            remoteTicket.assignedPa.isNotBlank() && remoteTicket.assignedPa != "PA Assigned" && remoteTicket.assignedPa != "PA" -> remoteTicket.assignedPa
                                            localTicket.assignedPa.isNotBlank() && localTicket.assignedPa != "PA Assigned" && localTicket.assignedPa != "PA" -> localTicket.assignedPa
                                            else -> remoteTicket.assignedPa.ifBlank { localTicket.assignedPa }
                                        }
                                        val combined = (localTicket.reports + remoteTicket.reports)
                                            .distinctBy { it.id to it.timestamp }
                                        val mergedJson = com.example.data.model.TicketReportJsonHelper.toJson(combined)

                                        val bestCitizenName = if (
                                            remoteTicket.citizenName.isNotBlank() &&
                                            !remoteTicket.citizenName.startsWith("Ticket #", ignoreCase = true) &&
                                            !remoteTicket.citizenName.startsWith("Constituent", ignoreCase = true)
                                        ) remoteTicket.citizenName else localTicket.citizenName

                                        ticketDao.updateTicket(
                                            localTicket.copy(
                                                citizenName = bestCitizenName,
                                                phoneNumber = if (remoteTicket.phoneNumber.isNotBlank()) remoteTicket.phoneNumber else localTicket.phoneNumber,
                                                location = remoteTicket.location.ifBlank { localTicket.location },
                                                assignedPa = effectivePa,
                                                reportsJson = if (mergedJson.isNotBlank()) mergedJson else localTicket.reportsJson
                                            )
                                        )
                                    } else {
                                        ticketDao.insertTicket(remoteTicket)
                                    }
                                }
                            }
                        }
                    }
                listeners.add(registration)
                Log.d("FirestoreTicketSync", "Listening to Firestore collection '$colName' registered.")
            } catch (e: Exception) {
                Log.e("FirestoreTicketSync", "Failed to register snapshot listener for '$colName'", e)
            }
        }

        // Real-time listener for PA credentials collections
        val paCollectionsToListen = listOf("pa_credentials", "pa_logins", "pa_users", "users", "pa_members", "pas", "staff", "credentials", "logins")
        for (colName in paCollectionsToListen) {
            try {
                val registration = db.collection(colName)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null || snapshot == null) return@addSnapshotListener
                        coroutineScope.launch {
                            paSyncMutex.withLock {
                                for (change in snapshot.documentChanges) {
                                    val doc = change.document
                                    when (change.type) {
                                        DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED -> {
                                            val remotePa = FirestoreTicketMapper.extractPaUserFromDocument(doc) ?: continue

                                            val rawPhone = remotePa.phoneNumber.trim()
                                            val cleanPhone = rawPhone.replace(Regex("[^0-9]"), "")
                                            val nameLower = remotePa.paName.trim().lowercase()
                                            val docId = doc.id

                                            val allLocal = ticketDao.getAllPaUsersList()
                                            val existing = allLocal.find { local ->
                                                (local.firestoreDocId != null && local.firestoreDocId == docId) ||
                                                (rawPhone.isNotBlank() && local.phoneNumber.trim() == rawPhone) ||
                                                (cleanPhone.isNotBlank() && local.phoneNumber.replace(Regex("[^0-9]"), "").trim() == cleanPhone) ||
                                                (nameLower.isNotBlank() && local.paName.trim().lowercase() == nameLower)
                                            }

                                            if (existing != null) {
                                                val updated = remotePa.copy(
                                                    id = existing.id,
                                                    createdAt = existing.createdAt
                                                )
                                                ticketDao.updatePaUser(updated)
                                                registerKnownPa(updated)
                                            } else {
                                                ticketDao.insertPaUser(remotePa)
                                                registerKnownPa(remotePa)
                                            }
                                        }
                                        DocumentChange.Type.REMOVED -> {
                                            val phone = doc.getString("phoneNumber") ?: doc.getString("phone") ?: ""
                                            val name = doc.getString("paName") ?: doc.getString("name") ?: ""
                                            if (phone.isNotBlank()) {
                                                ticketDao.deletePaUserByPhone(phone)
                                                val clean = phone.replace(Regex("[^0-9]"), "")
                                                if (clean.isNotBlank()) {
                                                    ticketDao.deletePaUserByPhone(clean)
                                                }
                                            }
                                            if (name.isNotBlank()) {
                                                ticketDao.deletePaUserByName(name)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                listeners.add(registration)
            } catch (e: Exception) {
                Log.e("FirestoreTicketSync", "Failed listening to PA collection '$colName'", e)
            }
        }

        // Real-time listener for Leader credentials collections across Web App & Leader App
        val leaderCollectionsToListen = listOf("leader_credentials", "leader_logins", "leaders", "admins", "admin_credentials", "admin_logins", "users", "credentials")
        for (colName in leaderCollectionsToListen) {
            try {
                val registration = db.collection(colName)
                    .addSnapshotListener { snapshot, error ->
                        if (error != null || snapshot == null) return@addSnapshotListener
                        coroutineScope.launch {
                            for (change in snapshot.documentChanges) {
                                if (change.type == DocumentChange.Type.ADDED || change.type == DocumentChange.Type.MODIFIED) {
                                    val doc = change.document
                                    val data = doc.data ?: continue
                                    val rawName = FirestoreTicketMapper.extractString(data, listOf("name", "leaderName", "username", "displayName", "adminName")) ?: "Jeevan"
                                    val phone = FirestoreTicketMapper.extractPhoneNumber(data)
                                    val pass = FirestoreTicketMapper.extractString(data, listOf("password", "pass", "pwd")) ?: "1234"
                                    val designation = FirestoreTicketMapper.extractString(data, listOf("designation", "role")) ?: "Leader / Admin"
                                    val jurisdiction = FirestoreTicketMapper.extractString(data, listOf("jurisdiction", "constituency")) ?: "Zone 4 - Central Constituency"

                                    val name = if (rawName.contains("Vishnu", ignoreCase = true)) "Jeevan" else rawName

                                    // If document in Firestore contains old "Vishnu" name, auto-update Firestore document to Jeevan
                                    if (rawName.contains("Vishnu", ignoreCase = true)) {
                                        try {
                                            doc.reference.update(
                                                mapOf(
                                                    "name" to "Jeevan",
                                                    "leaderName" to "Jeevan",
                                                    "adminName" to "Jeevan",
                                                    "username" to "Jeevan",
                                                    "displayName" to "Jeevan"
                                                )
                                            )
                                        } catch (e: Exception) {
                                            Log.e("FirestoreTicketSync", "Failed updating Vishnu doc in Firestore", e)
                                        }
                                    }

                                    val cleanPhone = phone.replace(Regex("[^0-9]"), "")
                                    if (cleanPhone == "8978925540" || cleanPhone.isNotBlank()) {
                                        onLeaderCredentialUpdate?.invoke(name, phone.ifBlank { "8978925540" }, pass, designation, jurisdiction)
                                    }
                                }
                            }
                        }
                    }
                listeners.add(registration)
            } catch (e: Exception) {
                Log.e("FirestoreTicketSync", "Failed listening to Leader collection '$colName'", e)
            }
        }
    }

    /**
     * Queries Firestore collections for an existing ticket matching the given ticket code/ID
     * or phone/mobile number BEFORE initiating a create operation.
     */
    suspend fun queryExistingTicket(
        ticketCode: String,
        phoneNumber: String
    ): TicketEntity? = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext null
        val rawCode = ticketCode.removePrefix("#").trim()
        val normEnteredNumber = ticketCode.trim().uppercase().removePrefix("#").removePrefix("T-").trim()
        val cleanPhone = phoneNumber.replace(Regex("[^0-9]"), "")

        if (rawCode.isBlank() && cleanPhone.isBlank()) return@withContext null

        val collections = listOf(
            "tickets", "Tickets", "grievances", "Grievances",
            "requests", "Requests", "issues", "Issues",
            "complaints", "Complaints", "citizen_tickets", "pa_tickets"
        )

        try {
            withTimeoutOrNull(4000L) {
                for (colName in collections) {
                    try {
                        // 1. Direct document lookup by ID
                        val docIdsToTry = listOfNotNull(
                            rawCode.takeIf { it.isNotBlank() },
                            "#$rawCode".takeIf { rawCode.isNotBlank() },
                            "T-$rawCode".takeIf { rawCode.isNotBlank() },
                            if (normEnteredNumber.isNotBlank()) "T-$normEnteredNumber" else null,
                            if (normEnteredNumber.isNotBlank()) normEnteredNumber else null
                        ).distinct()

                        for (docId in docIdsToTry) {
                            try {
                                val docSnap = db.collection(colName).document(docId).get().await()
                                if (docSnap.exists()) {
                                    val parsed = FirestoreTicketMapper.fromDocument(docSnap, ::resolvePaName)
                                    if (parsed != null) return@withTimeoutOrNull parsed
                                }
                            } catch (_: Exception) {}
                        }

                        // 2. Query by ticketCode / ticket_number / ticketId
                        if (normEnteredNumber.isNotBlank()) {
                            val codeFields = listOf("ticketCode", "ticket_code", "ticketNumber", "ticket_number", "ticketId", "ticket_id")
                            val codeValues = listOf(ticketCode, rawCode, "#$rawCode", "T-$normEnteredNumber", normEnteredNumber).distinct()
                            for (field in codeFields) {
                                for (valCandidate in codeValues) {
                                    try {
                                        val querySnap = db.collection(colName)
                                            .whereEqualTo(field, valCandidate)
                                            .limit(1)
                                            .get()
                                            .await()
                                        if (!querySnap.isEmpty) {
                                            val parsed = FirestoreTicketMapper.fromDocument(querySnap.documents.first(), ::resolvePaName)
                                            if (parsed != null) return@withTimeoutOrNull parsed
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }

                        // 3. Query by mobile / phone number
                        if (cleanPhone.length >= 7) {
                            val phoneFields = listOf("phoneNumber", "phone_number", "phone", "mobile", "contact", "citizenPhone", "citizen_phone", "cleanPhone")
                            val last10 = cleanPhone.takeLast(10)
                            val phoneCandidates = listOf(phoneNumber, cleanPhone, last10, "+91$last10").distinct()

                            for (field in phoneFields) {
                                for (phoneVal in phoneCandidates) {
                                    try {
                                        val querySnap = db.collection(colName)
                                            .whereEqualTo(field, phoneVal)
                                            .limit(1)
                                            .get()
                                            .await()
                                        if (!querySnap.isEmpty) {
                                            val parsed = FirestoreTicketMapper.fromDocument(querySnap.documents.first(), ::resolvePaName)
                                            if (parsed != null) return@withTimeoutOrNull parsed
                                        }
                                    } catch (_: Exception) {}
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("FirestoreTicketSync", "Error querying existing ticket in $colName", e)
                    }
                }
                null
            }
        } catch (e: Exception) {
            Log.e("FirestoreTicketSync", "Timeout or exception querying Firestore for existing ticket", e)
            null
        }
    }

    /**
     * Saves or updates a ticket in Firestore in real time so the Leader Web Application and Ticket Portal are updated instantly.
     */
    fun saveTicketToFirestore(ticket: TicketEntity) {
        val db = firestore ?: return
        val rawCode = ticket.ticketCode.removePrefix("#").trim()
        val codeWithHash = if (ticket.ticketCode.startsWith("#")) ticket.ticketCode else "#${ticket.ticketCode}"
        val data = FirestoreTicketMapper.toMap(ticket)

        val collections = listOf(
            "tickets", "Tickets", "grievances", "Grievances",
            "requests", "Requests", "issues", "Issues",
            "complaints", "Complaints", "citizen_tickets", "pa_tickets"
        )
        for (colName in collections) {
            try {
                db.collection(colName).document(rawCode).set(data, com.google.firebase.firestore.SetOptions.merge())
                db.collection(colName).document(codeWithHash).set(data, com.google.firebase.firestore.SetOptions.merge())
            } catch (e: Exception) {
                Log.e("FirestoreTicketSync", "Error saving ticket to $colName", e)
            }
        }
    }

    /**
     * Saves or updates Leader credentials/profile in Firestore in real time so Leader Web Application stays synchronized.
     */
    fun saveLeaderCredentialToFirestore(
        name: String = "Jeevan",
        phone: String = "8660982471",
        pass: String = "VN@1234",
        designation: String = "Leader / Admin",
        jurisdiction: String = "Zone 4 - Central Constituency"
    ) {
        val db = firestore ?: return
        val cleanPhone = phone.replace(Regex("[^0-9]"), "").ifBlank { "8660982471" }
        val leaderData = mapOf(
            "name" to name,
            "leaderName" to name,
            "adminName" to name,
            "username" to name,
            "displayName" to name,
            "phoneNumber" to phone,
            "phone" to phone,
            "mobile" to phone,
            "password" to pass,
            "pass" to pass,
            "designation" to designation,
            "role" to "Leader / Admin",
            "userType" to "Leader",
            "jurisdiction" to jurisdiction,
            "status" to "Active",
            "updatedAt" to System.currentTimeMillis()
        )

        val collections = listOf(
            "leader_credentials", "leader_logins", "leaders",
            "admins", "admin_credentials", "admin_logins",
            "users", "credentials", "profiles", "leaders_and_admins"
        )
        val docIds = listOf(
            cleanPhone,
            "leader_$cleanPhone",
            "admin_$cleanPhone",
            "leader",
            "admin",
            "default_leader",
            "current_leader",
            "leader_profile",
            "admin_profile",
            "1"
        )

        for (colName in collections) {
            val colRef = db.collection(colName)
            for (docId in docIds) {
                try {
                    colRef.document(docId).set(leaderData, com.google.firebase.firestore.SetOptions.merge())
                } catch (e: Exception) {
                    Log.e("FirestoreTicketSync", "Error saving Leader credential to $colName/$docId", e)
                }
            }
            try {
                colRef.get().addOnSuccessListener { snap ->
                    for (doc in snap.documents) {
                        val dData = doc.data ?: continue
                        val strData = dData.toString()
                        val docId = doc.id
                        if (strData.contains("Vishnu", ignoreCase = true) || 
                            docId.contains("vishnu", ignoreCase = true) ||
                            strData.contains("8978925540") ||
                            docId.contains("8978925540")) {
                            doc.reference.delete()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FirestoreTicketSync", "Error cleaning old admin documents in $colName", e)
            }
        }
    }

    /**
     * Deletes a ticket from the local database when its Firestore document is removed.
     * Strictly targets only the specific ticket document by its document ID / ticket code.
     */
    suspend fun deleteTicketByDoc(doc: DocumentSnapshot) {
        val docId = doc.id
        val codesToDelete = mutableSetOf<String>()
        codesToDelete.add(docId)
        codesToDelete.add("#$docId")

        val data = doc.data
        if (data != null) {
            val rawCode = FirestoreTicketMapper.extractString(
                data,
                listOf("ticketCode", "ticket_code", "ticketId", "ticket_id", "code", "id")
            )
            if (rawCode != null) {
                codesToDelete.add(rawCode)
                if (!rawCode.startsWith("#")) {
                    codesToDelete.add("#$rawCode")
                }
            }
        }

        ticketDao.deleteTicketsByCodes(codesToDelete.toList())

        // Ensure only local tickets matching this specific document ID or stripped code are purged
        val allLocal = ticketDao.getAllTicketsSync()
        for (t in allLocal) {
            val stripped = t.ticketCode.removePrefix("#").trim()
            val docIdStripped = docId.removePrefix("#").trim()
            if (stripped.equals(docIdStripped, ignoreCase = true) || t.ticketCode.equals(docId, ignoreCase = true)) {
                ticketDao.deleteTicket(t)
            }
        }
        Log.d("FirestoreTicketSync", "Deleted single ticket for doc $docId from local database.")
    }

    /**
     * Deletes a ticket by its ID or ticket code.
     */
    suspend fun deleteTicketByIdOrCode(codeOrId: String) {
        val codes = listOf(codeOrId, "#$codeOrId", codeOrId.removePrefix("#"))
        ticketDao.deleteTicketsByCodes(codes)
        val allLocal = ticketDao.getAllTicketsSync()
        for (t in allLocal) {
            val stripped = t.ticketCode.removePrefix("#").trim()
            if (stripped.equals(codeOrId.removePrefix("#"), ignoreCase = true)) {
                ticketDao.deleteTicket(t)
            }
        }
    }

    /**
     * Deletes ONLY the specific ticket document from Firestore using its document ID and ticket code.
     * Does NOT delete unrelated tickets, parent collections, or user credentials.
     */
    fun deleteTicketFromFirestore(ticket: TicketEntity) {
        val db = firestore ?: return
        val rawCode = ticket.ticketCode.removePrefix("#").trim()
        val codeWithHash = if (ticket.ticketCode.startsWith("#")) ticket.ticketCode else "#${ticket.ticketCode}"
        val collections = listOf(
            "tickets", "Tickets", "grievances", "Grievances",
            "requests", "Requests", "issues", "Issues",
            "complaints", "Complaints", "citizen_tickets", "pa_tickets"
        )

        for (colName in collections) {
            try {
                val colRef = db.collection(colName)
                if (rawCode.isNotBlank()) {
                    colRef.document(rawCode).delete()
                    colRef.document(codeWithHash).delete()

                    colRef.whereEqualTo("ticketCode", codeWithHash).get()
                        .addOnSuccessListener { snap ->
                            for (d in snap.documents) {
                                val dCode = d.getString("ticketCode") ?: d.getString("ticket_code") ?: ""
                                if (dCode.equals(codeWithHash, ignoreCase = true) || dCode.equals(rawCode, ignoreCase = true) || d.id == rawCode || d.id == codeWithHash) {
                                    d.reference.delete()
                                }
                            }
                        }
                    colRef.whereEqualTo("ticketCode", rawCode).get()
                        .addOnSuccessListener { snap ->
                            for (d in snap.documents) {
                                val dCode = d.getString("ticketCode") ?: d.getString("ticket_code") ?: ""
                                if (dCode.equals(codeWithHash, ignoreCase = true) || dCode.equals(rawCode, ignoreCase = true) || d.id == rawCode || d.id == codeWithHash) {
                                    d.reference.delete()
                                }
                            }
                        }
                }
            } catch (e: Exception) {
                Log.e("FirestoreTicketSync", "Error deleting ticket from $colName: ${e.message}")
            }
        }
    }

    /**
     * Deletes all tickets from Firestore so that the Ticket Portal and Firestore database are cleared.
     */
    fun deleteAllTicketsFromFirestore() {
        val db = firestore ?: return
        val collections = listOf(
            "tickets", "Tickets", "grievances", "Grievances",
            "requests", "Requests", "issues", "Issues",
            "complaints", "Complaints", "citizen_tickets", "pa_tickets"
        )
        for (colName in collections) {
            try {
                db.collection(colName).get()
                    .addOnSuccessListener { snap ->
                        for (d in snap.documents) d.reference.delete()
                    }
            } catch (e: Exception) {
                Log.e("FirestoreTicketSync", "Error clearing collection $colName", e)
            }
        }
    }

    /**
     * Syncs PA login credentials to Firestore so PA Portal can authenticate the PA.
     */
    fun savePaCredentialToFirestore(paUser: PaUserEntity) {
        val db = firestore ?: return
        val rawPhone = paUser.phoneNumber.trim()
        val cleanPhone = rawPhone.replace(Regex("[^0-9]"), "")
        val docIds = mutableSetOf<String>()
        if (cleanPhone.isNotBlank()) {
            docIds.add(cleanPhone)
            docIds.add("pa_$cleanPhone")
        }
        if (rawPhone.isNotBlank()) {
            docIds.add(rawPhone)
            docIds.add("pa_$rawPhone")
        }
        if (paUser.id > 0) {
            docIds.add("pa_${paUser.id}")
        }
        if (!paUser.firestoreDocId.isNullOrBlank()) {
            docIds.add(paUser.firestoreDocId!!)
        }
        if (docIds.isEmpty()) {
            docIds.add("pa_${System.currentTimeMillis()}")
        }

        val paData = mapOf(
            "id" to paUser.id,
            "paName" to paUser.paName,
            "name" to paUser.paName,
            "username" to paUser.paName,
            "displayName" to paUser.paName,
            "phoneNumber" to paUser.phoneNumber,
            "phone" to paUser.phoneNumber,
            "mobile" to paUser.phoneNumber,
            "password" to paUser.password,
            "pass" to paUser.password,
            "pwd" to paUser.password,
            "designation" to paUser.designation,
            "role" to "PA",
            "status" to paUser.status,
            "createdAt" to paUser.createdAt,
            "updatedAt" to System.currentTimeMillis(),
            "createdBy" to "Admin Leader Portal"
        )

        val collections = listOf("pa_credentials", "pa_logins", "pa_users", "users", "pa_members", "pas", "staff", "credentials", "logins")
        for (colName in collections) {
            try {
                val colRef = db.collection(colName)
                for (docId in docIds) {
                    colRef.document(docId).set(paData, SetOptions.merge())
                        .addOnSuccessListener {
                            Log.d("FirestoreTicketSync", "Successfully synced PA credentials for ${paUser.paName} to $colName/$docId")
                        }
                        .addOnFailureListener { e ->
                            Log.e("FirestoreTicketSync", "Failed syncing PA credentials to $colName/$docId: ${e.message}")
                        }
                }
            } catch (e: Exception) {
                Log.e("FirestoreTicketSync", "Error saving PA to $colName", e)
            }
        }
    }

    /**
     * Deletes PA credentials instantly and thoroughly from all Firestore collections and web application tables.
     */
    fun deletePaCredentialFromFirestore(paUser: PaUserEntity) {
        val db = firestore ?: return
        val cleanPhone = paUser.phoneNumber.replace(Regex("[^0-9]"), "").trim()
        val rawPhone = paUser.phoneNumber.trim()
        val collections = listOf("pa_credentials", "pa_logins", "pa_users", "users", "pa_members", "pas", "staff", "credentials", "logins")

        for (colName in collections) {
            try {
                val colRef = db.collection(colName)

                // 1. Direct Document Deletions by possible document IDs
                val docIdsToDelete = mutableSetOf<String>()
                if (cleanPhone.isNotBlank()) {
                    docIdsToDelete.add(cleanPhone)
                    docIdsToDelete.add("pa_$cleanPhone")
                }
                if (rawPhone.isNotBlank()) {
                    docIdsToDelete.add(rawPhone)
                    docIdsToDelete.add("pa_$rawPhone")
                }
                if (paUser.id > 0) {
                    docIdsToDelete.add("pa_${paUser.id}")
                }
                if (!paUser.firestoreDocId.isNullOrBlank()) {
                    docIdsToDelete.add(paUser.firestoreDocId)
                }
                if (paUser.paName.isNotBlank()) {
                    docIdsToDelete.add(paUser.paName)
                    docIdsToDelete.add(paUser.paName.lowercase().replace(" ", "_"))
                }

                for (id in docIdsToDelete) {
                    colRef.document(id).delete()
                        .addOnSuccessListener {
                            Log.d("FirestoreTicketSync", "Deleted PA document '$id' from '$colName'")
                        }
                }

                // 2. Query-based Deletions to clean up matching records across web app collections
                val fieldsToMatch = mutableListOf<Pair<String, String>>()
                if (rawPhone.isNotBlank()) {
                    fieldsToMatch.add("phoneNumber" to rawPhone)
                    fieldsToMatch.add("phone" to rawPhone)
                    fieldsToMatch.add("username" to rawPhone)
                }
                if (cleanPhone.isNotBlank()) {
                    fieldsToMatch.add("phoneNumber" to cleanPhone)
                    fieldsToMatch.add("phone" to cleanPhone)
                    fieldsToMatch.add("username" to cleanPhone)
                }
                if (paUser.paName.isNotBlank()) {
                    fieldsToMatch.add("paName" to paUser.paName)
                    fieldsToMatch.add("name" to paUser.paName)
                }

                for ((field, value) in fieldsToMatch) {
                    colRef.whereEqualTo(field, value).get()
                        .addOnSuccessListener { snap ->
                            for (d in snap.documents) {
                                d.reference.delete()
                                    .addOnSuccessListener {
                                        Log.d("FirestoreTicketSync", "Deleted matching PA doc '${d.id}' from '$colName' ($field=$value)")
                                    }
                            }
                        }
                        .addOnFailureListener { e ->
                            Log.e("FirestoreTicketSync", "Error querying $colName for $field=$value: ${e.message}")
                        }
                }
            } catch (e: Exception) {
                Log.e("FirestoreTicketSync", "Error deleting PA from $colName", e)
            }
        }

        // 3. Mark sessions/tokens as revoked in real time
        val revocationData = mapOf(
            "status" to "deleted",
            "revoked" to true,
            "phoneNumber" to rawPhone,
            "phone" to cleanPhone,
            "paName" to paUser.paName,
            "revokedAt" to System.currentTimeMillis(),
            "reason" to "Credentials deleted by Leader"
        )
        val sessionCollections = listOf("pa_sessions", "revoked_credentials", "pa_tokens", "active_sessions")
        for (sessCol in sessionCollections) {
            try {
                if (cleanPhone.isNotBlank()) db.collection(sessCol).document(cleanPhone).set(revocationData)
                if (rawPhone.isNotBlank()) db.collection(sessCol).document(rawPhone).set(revocationData)
                if (paUser.paName.isNotBlank()) db.collection(sessCol).document(paUser.paName.lowercase().replace(" ", "_")).set(revocationData)
            } catch (e: Exception) {
                Log.e("FirestoreTicketSync", "Error writing revocation to $sessCol", e)
            }
        }
    }

    fun stopListening() {
        for (listener in listeners) {
            listener.remove()
        }
        listeners.clear()
        collectionDocIds.clear()
    }

    /**
     * One-time direct server refresh to sync all tickets and PA data from Firestore to the local database.
     */
    suspend fun refresh() = withContext(Dispatchers.IO) {
        val db = firestore ?: return@withContext
        // Ensure listeners are initialized
        startListening()

        val collectionsToFetch = listOf(
            "tickets", "Tickets", "grievances", "Grievances",
            "requests", "Requests", "issues", "Issues",
            "complaints", "Complaints", "citizen_tickets", "pa_tickets"
        )

        for (colName in collectionsToFetch) {
            try {
                val snapshot = db.collection(colName).get().await()
                if (!snapshot.isEmpty) {
                    ticketDao.deleteTicketsByCitizenNames(sampleNames)
                }

                val currentIds = snapshot.documents.map { it.id }.toSet()
                val previousIds = collectionDocIds[colName]
                if (previousIds != null) {
                    val removedIds = previousIds - currentIds
                    for (removedId in removedIds) {
                        deleteTicketByIdOrCode(removedId)
                    }
                }
                collectionDocIds[colName] = currentIds.toMutableSet()

                for (doc in snapshot.documents) {
                    val remoteTicket = FirestoreTicketMapper.fromDocument(doc, ::resolvePaName) ?: continue
                    val localTicket = ticketDao.getTicketByCode(remoteTicket.ticketCode)
                        ?: ticketDao.findExistingTicket(
                            remoteTicket.ticketCode,
                            remoteTicket.citizenName,
                            remoteTicket.phoneNumber
                        )

                    if (localTicket != null) {
                        val effectivePa = when {
                            remoteTicket.assignedPa.isNotBlank() && remoteTicket.assignedPa != "PA Assigned" && remoteTicket.assignedPa != "PA" -> remoteTicket.assignedPa
                            localTicket.assignedPa.isNotBlank() && localTicket.assignedPa != "PA Assigned" && localTicket.assignedPa != "PA" -> localTicket.assignedPa
                            else -> remoteTicket.assignedPa.ifBlank { localTicket.assignedPa }
                        }
                        val combined = (localTicket.reports + remoteTicket.reports)
                            .distinctBy { it.id to it.timestamp }
                        val mergedJson = com.example.data.model.TicketReportJsonHelper.toJson(combined)
                        ticketDao.updateTicket(
                            remoteTicket.copy(
                                id = localTicket.id,
                                assignedPa = effectivePa,
                                reportsJson = if (mergedJson.isNotBlank()) mergedJson else remoteTicket.reportsJson
                            )
                        )
                    } else {
                        val cleanRemotePhone = remoteTicket.phoneNumber.replace(Regex("[^0-9]"), "")
                        val existingUnsolved = ticketDao.getUnsolvedTickets().firstOrNull { t ->
                            val tClean = t.phoneNumber.replace(Regex("[^0-9]"), "")
                            (cleanRemotePhone.isNotBlank() && tClean == cleanRemotePhone) ||
                            (remoteTicket.phoneNumber.isNotBlank() && t.phoneNumber.trim().equals(remoteTicket.phoneNumber.trim(), ignoreCase = true))
                        }

                        if (existingUnsolved != null) {
                            val effectivePa = when {
                                remoteTicket.assignedPa.isNotBlank() && remoteTicket.assignedPa != "PA Assigned" && remoteTicket.assignedPa != "PA" -> remoteTicket.assignedPa
                                existingUnsolved.assignedPa.isNotBlank() && existingUnsolved.assignedPa != "PA Assigned" && existingUnsolved.assignedPa != "PA" -> existingUnsolved.assignedPa
                                else -> remoteTicket.assignedPa.ifBlank { existingUnsolved.assignedPa }
                            }
                            val combined = (existingUnsolved.reports + remoteTicket.reports)
                                .distinctBy { it.id to it.timestamp }
                            val mergedJson = com.example.data.model.TicketReportJsonHelper.toJson(combined)
                            ticketDao.updateTicket(
                                existingUnsolved.copy(
                                    description = remoteTicket.description.ifBlank { existingUnsolved.description },
                                    subCategory = remoteTicket.subCategory.ifBlank { existingUnsolved.subCategory },
                                    assignedPa = effectivePa,
                                    reportsJson = mergedJson
                                )
                            )
                        } else {
                            ticketDao.insertTicket(remoteTicket)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FirestoreTicketSync", "Error refreshing collection '$colName'", e)
            }
        }

        // Refresh PA credentials
        val paCollections = listOf("pas", "pa_credentials", "pa_users")
        for (colName in paCollections) {
            try {
                val snapshot = db.collection(colName).get().await()
                paSyncMutex.withLock {
                    for (doc in snapshot.documents) {
                        val remotePa = FirestoreTicketMapper.extractPaUserFromDocument(doc) ?: continue
                        registerKnownPa(remotePa)
                        val rawPhone = remotePa.phoneNumber.trim()
                        val cleanPhone = rawPhone.replace(Regex("[^0-9]"), "")
                        val allLocal = ticketDao.getAllPaUsersList()
                        val existing = allLocal.find { local ->
                            (local.firestoreDocId != null && local.firestoreDocId == doc.id) ||
                            (rawPhone.isNotBlank() && local.phoneNumber.trim() == rawPhone) ||
                            (cleanPhone.isNotBlank() && local.phoneNumber.replace(Regex("[^0-9]"), "").trim() == cleanPhone)
                        }
                        if (existing != null) {
                            ticketDao.updatePaUser(remotePa.copy(id = existing.id, createdAt = existing.createdAt))
                        } else {
                            ticketDao.insertPaUser(remotePa)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("FirestoreTicketSync", "Error refreshing PA collection '$colName'", e)
            }
        }
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
        kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { result ->
                if (continuation.isActive) continuation.resume(result) {}
            }
            addOnFailureListener { exception ->
                if (continuation.isActive) continuation.resumeWith(Result.failure(exception))
            }
        }
}

