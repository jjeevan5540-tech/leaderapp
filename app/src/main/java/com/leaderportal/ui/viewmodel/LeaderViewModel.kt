package com.leaderportal.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.leaderportal.data.local.AppDatabase
import com.leaderportal.data.model.ChatMessageEntity
import com.leaderportal.data.model.PaUserEntity
import com.leaderportal.data.model.TicketEntity
import com.leaderportal.data.model.TimelineEventEntity
import com.leaderportal.data.repository.TicketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.concurrent.TimeUnit

enum class DashboardTab {
    OVERVIEW,
    TICKETS
}

enum class CategoryFilter(val label: String) {
    ALL("All Categories"),
    PERSONAL("Personal"),
    ISSUE("Issue")
}

enum class StatusFilter(val label: String) {
    ALL("All Status"),
    SOLVED("Solved"),
    UNSOLVED("Unsolved")
}

enum class DateFilter(val label: String) {
    ALL("All Time"),
    TODAY("Today"),
    LAST_7_DAYS("Last 7 Days"),
    THIS_MONTH("This Month")
}

enum class SortOrder(val label: String) {
    DATE_DESC("Date & Time (Newest)"),
    DATE_ASC("Date & Time (Oldest)"),
    ID_ASC("Ticket ID (Asc)"),
    ID_DESC("Ticket ID (Desc)"),
    STATUS("Status (Unsolved First)")
}

enum class ViewDisplayMode {
    TABLE,
    CARDS
}

data class LeaderUser(
    val name: String = "Jeevan",
    val phone: String = "8660982471",
    val designation: String = "Constituency Leader / Admin",
    val jurisdiction: String = "Central Constituency"
)

data class DashboardStats(
    val totalTickets: Int = 0,
    val solvedCount: Int = 0,
    val unsolvedCount: Int = 0,
    val personalCount: Int = 0,
    val issueCount: Int = 0,
    val solvedRate: Float = 0f,
    val highPriorityPending: Int = 0
)

class LeaderViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TicketRepository

    // Auth State
    private val _isLoggedIn = MutableStateFlow(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn.asStateFlow()

    private val _currentUser = MutableStateFlow(LeaderUser())
    val currentUser: StateFlow<LeaderUser> = _currentUser.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    private val _userType = MutableStateFlow("LEADER") // "LEADER" or "PA"
    val userType: StateFlow<String> = _userType.asStateFlow()

    private val _loggedPaPhone = MutableStateFlow<String?>(null)
    val loggedPaPhone: StateFlow<String?> = _loggedPaPhone.asStateFlow()

    // Navigation Tab (Overview is default upon login)
    private val _currentTab = MutableStateFlow(DashboardTab.OVERVIEW)
    val currentTab: StateFlow<DashboardTab> = _currentTab.asStateFlow()

    // Filters and Controls
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _categoryFilter = MutableStateFlow(CategoryFilter.ALL)
    val categoryFilter: StateFlow<CategoryFilter> = _categoryFilter.asStateFlow()

    private val _statusFilter = MutableStateFlow(StatusFilter.ALL)
    val statusFilter: StateFlow<StatusFilter> = _statusFilter.asStateFlow()

    private val _dateFilter = MutableStateFlow(DateFilter.ALL)
    val dateFilter: StateFlow<DateFilter> = _dateFilter.asStateFlow()

    private val _paFilter = MutableStateFlow("All")
    val paFilter: StateFlow<String> = _paFilter.asStateFlow()

    private val _sortOrder = MutableStateFlow(SortOrder.DATE_DESC)
    val sortOrder: StateFlow<SortOrder> = _sortOrder.asStateFlow()

    private val _viewDisplayMode = MutableStateFlow(ViewDisplayMode.CARDS)
    val viewDisplayMode: StateFlow<ViewDisplayMode> = _viewDisplayMode.asStateFlow()

    // Selected ticket for modal / detail view
    private val _selectedTicket = MutableStateFlow<TicketEntity?>(null)
    val selectedTicket: StateFlow<TicketEntity?> = _selectedTicket.asStateFlow()

    // Dialogs
    private val _showRaiseTicketDialog = MutableStateFlow(false)
    val showRaiseTicketDialog: StateFlow<Boolean> = _showRaiseTicketDialog.asStateFlow()

    private val _showPaCredentialsDialog = MutableStateFlow(false)
    val showPaCredentialsDialog: StateFlow<Boolean> = _showPaCredentialsDialog.asStateFlow()

    // Pull to Refresh State
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            try {
                repository.refresh()
                repository.mergeDuplicateTicketsByPhone()
                verifyActiveSession()
                delay(600)
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    init {
        val db = AppDatabase.getDatabase(application)
        repository = TicketRepository(db.ticketDao())

        // Restore active session if present
        restoreSession()

        viewModelScope.launch {
            repository.checkAndSeedDefaultPaUsers(application)
            repository.checkAndSeedInitialData()
            repository.mergeDuplicateTicketsByPhone()
        }

        // Real-time listener for Leader credentials updates from Leader Web Application / Firestore
        repository.firestoreSyncService.onPaStatusRevoked = { revokedTarget ->
            if (_isLoggedIn.value && _userType.value == "PA") {
                val activePhone = _loggedPaPhone.value ?: ""
                val activeName = _currentUser.value.name
                val cleanActive = activePhone.replace(Regex("[^0-9]"), "")
                val cleanTarget = revokedTarget.replace(Regex("[^0-9]"), "")
                if ((cleanTarget.isNotBlank() && cleanTarget == cleanActive) ||
                    (revokedTarget.isNotBlank() && (activePhone.contains(revokedTarget, ignoreCase = true) || activeName.contains(revokedTarget, ignoreCase = true)))) {
                    handlePaRevocation()
                }
            }
        }

        viewModelScope.launch {
            repository.allPaUsers.collect { paList ->
                if (_isLoggedIn.value && _userType.value == "PA") {
                    val activePhone = _loggedPaPhone.value
                    if (activePhone.isNullOrBlank()) {
                        handlePaRevocation()
                    } else {
                        val cleanActive = activePhone.replace(Regex("[^0-9]"), "")
                        val pa = paList.find { 
                            it.phoneNumber == activePhone || 
                            it.phoneNumber.replace(Regex("[^0-9]"), "") == cleanActive 
                        }
                        if (pa == null || pa.status.equals("Inactive", ignoreCase = true) || pa.status.equals("Disabled", ignoreCase = true) || pa.status.equals("Deleted", ignoreCase = true)) {
                            handlePaRevocation()
                        }
                    }
                }
            }
        }

        repository.firestoreSyncService.onLeaderCredentialUpdate = { name, phone, pass, desig, juris ->
            val finalName = if (name.contains("Vishnu", ignoreCase = true)) "Jeevan" else name
            if (_isLoggedIn.value && _userType.value == "LEADER") {
                _currentUser.value = LeaderUser(
                    name = finalName,
                    phone = phone,
                    designation = desig,
                    jurisdiction = juris
                )
                try {
                    val prefs = getApplication<Application>().getSharedPreferences("leader_auth_session", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putString("leader_name", finalName)
                        .putString("leader_phone", phone)
                        .putString("leader_designation", desig)
                        .putString("leader_jurisdiction", juris)
                        .apply()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }

        // Real-time collector: if logged-in PA's credentials are deleted, log them out immediately with message
        viewModelScope.launch {
            repository.allPaUsers.collect { paList ->
                val currentType = _userType.value
                val activePhone = _loggedPaPhone.value
                if (_isLoggedIn.value && currentType == "PA" && !activePhone.isNullOrBlank()) {
                    val cleanActive = activePhone.replace(Regex("[^0-9]"), "").trim()
                    val matchingPa = paList.find { pa ->
                        val cleanPaPhone = pa.phoneNumber.replace(Regex("[^0-9]"), "").trim()
                        (cleanPaPhone.isNotBlank() && cleanPaPhone == cleanActive) ||
                        (pa.phoneNumber.trim() == activePhone.trim())
                    }
                    if (matchingPa == null || matchingPa.status.equals("Inactive", ignoreCase = true)) {
                        handlePaRevocation()
                    }
                }
            }
        }
    }

    fun handlePaRevocation() {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("leader_auth_session", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        _isLoggedIn.value = false
        _userType.value = "LEADER"
        _loggedPaPhone.value = null
        _loginError.value = "Your credentials have been deleted. Please contact your Leader."
    }

    fun verifyActiveSession() {
        if (!_isLoggedIn.value) return
        if (_userType.value == "PA") {
            val activePhone = _loggedPaPhone.value
            if (activePhone.isNullOrBlank()) {
                handlePaRevocation()
                return
            }
            viewModelScope.launch {
                val pa = repository.getPaByPhone(activePhone)
                if (pa == null || pa.status.equals("Inactive", ignoreCase = true)) {
                    handlePaRevocation()
                }
            }
        }
    }

    private fun restoreSession() {
        try {
            val prefs = getApplication<Application>().getSharedPreferences("leader_auth_session", Context.MODE_PRIVATE)
            val wasLoggedIn = prefs.getBoolean("is_logged_in", false)
            val userTypeStored = prefs.getString("user_type", "LEADER") ?: "LEADER"

            if (wasLoggedIn) {
                if (userTypeStored == "PA") {
                    val paPhone = prefs.getString("pa_phone", "") ?: ""
                    if (paPhone.isBlank()) {
                        handlePaRevocation()
                        return
                    }
                    viewModelScope.launch {
                        val pa = repository.getPaByPhone(paPhone)
                        if (pa == null || pa.status.equals("Inactive", ignoreCase = true)) {
                            handlePaRevocation()
                        } else {
                            _userType.value = "PA"
                            _loggedPaPhone.value = pa.phoneNumber
                            _currentUser.value = LeaderUser(
                                name = pa.paName,
                                phone = pa.phoneNumber,
                                designation = pa.designation,
                                jurisdiction = "Personal Assistant"
                            )
                            _isLoggedIn.value = true
                        }
                    }
                } else {
                    val rawName = prefs.getString("leader_name", "Jeevan") ?: "Jeevan"
                    val name = if (rawName.contains("Vishnu", ignoreCase = true)) "Jeevan" else rawName
                    if (rawName.contains("Vishnu", ignoreCase = true)) {
                        prefs.edit().putString("leader_name", "Jeevan").apply()
                    }
                    val phone = prefs.getString("leader_phone", "8660982471") ?: "8660982471"
                    val designation = prefs.getString("leader_designation", "Leader / Admin") ?: "Leader / Admin"
                    val jurisdiction = prefs.getString("leader_jurisdiction", "Zone 4 - Central Constituency") ?: "Zone 4 - Central Constituency"

                    _userType.value = "LEADER"
                    _loggedPaPhone.value = null
                    _currentUser.value = LeaderUser(
                        name = name,
                        phone = phone,
                        designation = designation,
                        jurisdiction = jurisdiction
                    )
                    _isLoggedIn.value = true
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    val allPaUsers: StateFlow<List<PaUserEntity>> by lazy {
        repository.allPaUsers.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    }

    fun setShowPaCredentialsDialog(show: Boolean) {
        _showPaCredentialsDialog.value = show
    }

    fun savePaCredential(
        paName: String,
        phoneNumber: String,
        password: String,
        designation: String = "Personal Assistant",
        status: String = "Active"
    ) {
        viewModelScope.launch {
            repository.savePaCredential(paName, phoneNumber, password, designation, status)
        }
    }

    fun deletePaCredential(paUser: PaUserEntity) {
        viewModelScope.launch {
            val cleanPhone = paUser.phoneNumber.replace(Regex("[^0-9]"), "").trim()
            val activePhone = _loggedPaPhone.value?.replace(Regex("[^0-9]"), "")?.trim()
            val isCurrentSessionDeleted = _userType.value == "PA" &&
                    (!activePhone.isNullOrBlank() && activePhone == cleanPhone)

            repository.deletePaCredential(paUser)

            if (isCurrentSessionDeleted) {
                handlePaRevocation()
            }
        }
    }


    fun deleteTicket(ticket: TicketEntity) {
        viewModelScope.launch {
            repository.deleteTicket(ticket)
            if (_selectedTicket.value?.id == ticket.id) {
                _selectedTicket.value = null
            }
        }
    }

    fun clearAllTickets() {
        viewModelScope.launch {
            repository.clearAllData(deleteFromFirestore = true)
            _selectedTicket.value = null
        }
    }

    // Raw tickets list
    val allTickets: StateFlow<List<TicketEntity>> = repository.allTickets
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Calculated Overview Stats
    val stats: StateFlow<DashboardStats> = repository.allTickets.combine(_currentUser) { list, _ ->
        val total = list.size
        val solved = list.count { it.isSolved }
        val unsolved = total - solved
        // Count entries / reports across tickets for Personal vs Issue
        val allReports = list.flatMap { it.reports }
        val personal = allReports.count { it.category.equals("Personal", ignoreCase = true) }
        val issue = allReports.count { it.category.equals("Issue", ignoreCase = true) }
        val solvedRate = if (total > 0) (solved.toFloat() / total.toFloat()) * 100f else 0f
        val highPriorityPending = list.count { !it.isSolved && it.priority.equals("High", ignoreCase = true) }

        DashboardStats(
            totalTickets = total,
            solvedCount = solved,
            unsolvedCount = unsolved,
            personalCount = personal,
            issueCount = issue,
            solvedRate = solvedRate,
            highPriorityPending = highPriorityPending
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardStats()
    )

    // Available PAs for filter
    val availablePas: StateFlow<List<String>> = repository.allTickets.combine(_searchQuery) { list, _ ->
        val pas = list.map { it.assignedPa }.filter { it.isNotBlank() }.distinct().sorted()
        listOf("All") + pas
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = listOf("All", "Leader Direct")
    )

    // Filtered & Sorted Tickets
    val filteredTickets: StateFlow<List<TicketEntity>> = combine(
        repository.allTickets,
        _searchQuery,
        _categoryFilter,
        _statusFilter,
        _dateFilter,
        _paFilter,
        _sortOrder
    ) { args: Array<Any> ->
        @Suppress("UNCHECKED_CAST")
        val rawList = args[0] as List<TicketEntity>
        val query = (args[1] as String).trim().lowercase()
        val cat = args[2] as CategoryFilter
        val stat = args[3] as StatusFilter
        val dateF = args[4] as DateFilter
        val pa = args[5] as String
        val sort = args[6] as SortOrder

        val now = System.currentTimeMillis()
        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        val startOfToday = calendar.timeInMillis
        val startOfWeek = now - TimeUnit.DAYS.toMillis(7)
        val startOfMonth = now - TimeUnit.DAYS.toMillis(30)

        rawList.filter { ticket ->
            // Search filter: Name / Phone / Ticket ID
            val matchesQuery = query.isEmpty() ||
                ticket.citizenName.lowercase().contains(query) ||
                ticket.phoneNumber.contains(query) ||
                ticket.ticketCode.lowercase().contains(query) ||
                ticket.subCategory.lowercase().contains(query) ||
                ticket.location.lowercase().contains(query) ||
                ticket.effectiveAddress.lowercase().contains(query) ||
                ticket.effectiveDescription.lowercase().contains(query)

            // Category filter: checks if any report/category in this ticket matches
            val matchesCategory = when (cat) {
                CategoryFilter.ALL -> true
                CategoryFilter.PERSONAL -> ticket.distinctCategories.any { it.equals("Personal", ignoreCase = true) }
                CategoryFilter.ISSUE -> ticket.distinctCategories.any { it.equals("Issue", ignoreCase = true) }
            }

            // Status filter
            val matchesStatus = when (stat) {
                StatusFilter.ALL -> true
                StatusFilter.SOLVED -> ticket.isSolved
                StatusFilter.UNSOLVED -> !ticket.isSolved
            }

            // Date filter (matches against latest report timestamp)
            val matchesDate = when (dateF) {
                DateFilter.ALL -> true
                DateFilter.TODAY -> ticket.latestTimestamp >= startOfToday
                DateFilter.LAST_7_DAYS -> ticket.latestTimestamp >= startOfWeek
                DateFilter.THIS_MONTH -> ticket.latestTimestamp >= startOfMonth
            }

            // PA filter
            val matchesPa = (pa == "All") || ticket.assignedPa.equals(pa, ignoreCase = true) || ticket.reports.any { it.paName.equals(pa, ignoreCase = true) }

            matchesQuery && matchesCategory && matchesStatus && matchesDate && matchesPa
        }.let { list ->
            // Sorting
            when (sort) {
                SortOrder.DATE_DESC -> list.sortedByDescending { it.latestTimestamp }
                SortOrder.DATE_ASC -> list.sortedBy { it.latestTimestamp }
                SortOrder.ID_ASC -> list.sortedBy { it.id }
                SortOrder.ID_DESC -> list.sortedByDescending { it.id }
                SortOrder.STATUS -> list.sortedWith(
                    compareBy<TicketEntity> { it.isSolved }
                        .thenByDescending { it.latestTimestamp }
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Chat messages for selected ticket
    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedTicketChat: StateFlow<List<ChatMessageEntity>> = _selectedTicket
        .flatMapLatest { ticket ->
            if (ticket == null) flowOf(emptyList())
            else repository.getChatMessages(ticket.id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Timeline events for selected ticket
    @OptIn(ExperimentalCoroutinesApi::class)
    val selectedTicketTimeline: StateFlow<List<TimelineEventEntity>> = _selectedTicket
        .flatMapLatest { ticket ->
            if (ticket == null) flowOf(emptyList())
            else repository.getTimelineEvents(ticket.id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // Authentication actions & Firebase Task await extension
    suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result ->
            if (continuation.isActive) {
                continuation.resume(result) {}
            }
        }
        addOnFailureListener { exception ->
            if (continuation.isActive) {
                continuation.resumeWith(Result.failure(exception))
            }
        }
    }

    fun login(emailOrPhone: String, password: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val input = emailOrPhone.trim()
            _loginError.value = null

            if (input.isBlank()) {
                _loginError.value = "Please enter email address or phone number"
                onResult(false, _loginError.value)
                return@launch
            }
            if (password.trim().isEmpty()) {
                _loginError.value = "Please enter password"
                onResult(false, _loginError.value)
                return@launch
            }

            val cleanPhone = input.replace(Regex("[^0-9]"), "").trim()

            // 1. Check Personal Assistant (PA) credentials in local Room database
            if (cleanPhone.isNotEmpty()) {
                val paUser = repository.getPaByPhone(cleanPhone)
                if (paUser != null) {
                    if (paUser.status.equals("Inactive", ignoreCase = true)) {
                        _loginError.value = "Your credentials have been deactivated. Please contact your Leader."
                        onResult(false, _loginError.value)
                        return@launch
                    }
                    if (paUser.password.trim() != password.trim()) {
                        _loginError.value = "Incorrect password. Please try again."
                        onResult(false, _loginError.value)
                        return@launch
                    }

                    try {
                        val prefs = getApplication<Application>().getSharedPreferences("leader_auth_session", Context.MODE_PRIVATE)
                        prefs.edit()
                            .putBoolean("is_logged_in", true)
                            .putString("user_type", "PA")
                            .putString("pa_phone", paUser.phoneNumber)
                            .putString("pa_name", paUser.paName)
                            .putString("pa_designation", paUser.designation)
                            .putLong("login_timestamp", System.currentTimeMillis())
                            .apply()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }

                    _userType.value = "PA"
                    _loggedPaPhone.value = paUser.phoneNumber
                    _currentUser.value = LeaderUser(
                        name = paUser.paName,
                        phone = paUser.phoneNumber,
                        designation = paUser.designation,
                        jurisdiction = "Personal Assistant"
                    )
                    _isLoggedIn.value = true
                    _currentTab.value = DashboardTab.OVERVIEW
                    onResult(true, null)
                    return@launch
                }
            }

            // 2. Leader / Administrator Authentication
            val isLeaderPhone = cleanPhone == "8660982471"

            if (isLeaderPhone) {
                if (password.trim() != "VN@1234") {
                    _loginError.value = "Incorrect password. Please try again."
                    onResult(false, _loginError.value)
                    return@launch
                }
                try {
                    val prefs = getApplication<Application>().getSharedPreferences("leader_auth_session", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putBoolean("is_logged_in", true)
                        .putString("user_type", "LEADER")
                        .putString("leader_name", "Leader Jeevan")
                        .putString("leader_phone", "8660982471")
                        .putString("leader_designation", "Leader / Admin")
                        .putString("leader_jurisdiction", "Constituency Administration")
                        .putLong("login_timestamp", System.currentTimeMillis())
                        .apply()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                _userType.value = "LEADER"
                _loggedPaPhone.value = null
                _currentUser.value = LeaderUser(
                    name = "Leader Jeevan",
                    phone = "8660982471",
                    designation = "Leader / Admin",
                    jurisdiction = "Constituency Administration"
                )
                _isLoggedIn.value = true
                _currentTab.value = DashboardTab.OVERVIEW
                onResult(true, null)
                return@launch
            }

            // 3. Fallback: Firebase Authentication
            try {
                com.google.firebase.auth.FirebaseAuth.getInstance()
                    .signInWithEmailAndPassword(input, password.trim())
                    .await()

                try {
                    val prefs = getApplication<Application>().getSharedPreferences("leader_auth_session", Context.MODE_PRIVATE)
                    prefs.edit()
                        .putBoolean("is_logged_in", true)
                        .putString("user_type", "LEADER")
                        .putString("leader_name", "Leader Jeevan")
                        .putString("leader_phone", "8978925540")
                        .putString("leader_designation", "Leader / Admin")
                        .putString("leader_jurisdiction", "Constituency Administration")
                        .putLong("login_timestamp", System.currentTimeMillis())
                        .apply()
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                _userType.value = "LEADER"
                _loggedPaPhone.value = null
                _currentUser.value = LeaderUser(
                    name = "Leader Jeevan",
                    phone = "8978925540",
                    designation = "Leader / Admin",
                    jurisdiction = "Constituency Administration"
                )
                _isLoggedIn.value = true
                _currentTab.value = DashboardTab.OVERVIEW
                onResult(true, null)
            } catch (e: Exception) {
                _loginError.value = "Account not found or incorrect login details."
                onResult(false, _loginError.value)
            }
        }
    }

    fun forgotPassword(email: String, onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (email.isBlank() || !email.contains("@")) {
                    onResult(false, "Please enter a valid email address.")
                    return@launch
                }
                com.google.firebase.auth.FirebaseAuth.getInstance()
                    .sendPasswordResetEmail(email.trim())
                    .await()
                onResult(true, "Password reset link sent to your email address.")
            } catch (e: Exception) {
                onResult(false, e.localizedMessage ?: "Failed to send password reset email.")
            }
        }
    }

    fun logout() {
        // Clear active persistent session
        try {
            val prefs = getApplication<Application>().getSharedPreferences("leader_auth_session", Context.MODE_PRIVATE)
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }

        _isLoggedIn.value = false
        _currentTab.value = DashboardTab.OVERVIEW
        _selectedTicket.value = null
        _searchQuery.value = ""
        _categoryFilter.value = CategoryFilter.ALL
        _statusFilter.value = StatusFilter.ALL
        _dateFilter.value = DateFilter.ALL
    }

    fun selectTab(tab: DashboardTab) {
        _currentTab.value = tab
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setCategoryFilter(category: CategoryFilter) {
        _categoryFilter.value = category
    }

    fun setStatusFilter(status: StatusFilter) {
        _statusFilter.value = status
    }

    fun setDateFilter(dateFilter: DateFilter) {
        _dateFilter.value = dateFilter
    }

    fun setPaFilter(pa: String) {
        _paFilter.value = pa
    }

    fun setSortOrder(order: SortOrder) {
        _sortOrder.value = order
    }

    fun setViewDisplayMode(mode: ViewDisplayMode) {
        _viewDisplayMode.value = mode
    }

    private val _notificationBanner = MutableStateFlow<String?>(null)
    val notificationBanner: StateFlow<String?> = _notificationBanner.asStateFlow()

    fun dismissNotificationBanner() {
        _notificationBanner.value = null
    }

    fun selectTicket(ticket: TicketEntity?) {
        _selectedTicket.value = ticket
        if (ticket != null) {
            viewModelScope.launch {
                repository.markTicketAsViewed(ticket)
            }
        }
    }

    fun setShowRaiseTicketDialog(show: Boolean) {
        _showRaiseTicketDialog.value = show
    }

    // Toggle Solved / Unsolved with ownership
    fun toggleStatus(ticket: TicketEntity, resolutionNotes: String? = null) {
        viewModelScope.launch {
            val newStatus = !ticket.isSolved
            repository.toggleSolvedStatus(
                ticket = ticket,
                newStatus = newStatus,
                resolutionNotes = resolutionNotes,
                actorName = "Leader Jeevan"
            )
            // If current ticket is selected, update it
            if (_selectedTicket.value?.id == ticket.id) {
                _selectedTicket.value = ticket.copy(
                    isSolved = newStatus,
                    solvedAt = if (newStatus) System.currentTimeMillis() else null,
                    resolutionNotes = if (newStatus) resolutionNotes ?: ticket.resolutionNotes else null
                )
            }
        }
    }

    fun sendChatMessage(ticketId: Int, message: String) {
        if (message.isBlank()) return
        viewModelScope.launch {
            repository.sendChatMessage(
                ticketId = ticketId,
                message = message.trim(),
                senderName = "Leader Jeevan",
                isLeader = true
            )
        }
    }

    fun createTicket(
        ticketNumber: String = "",
        citizenName: String,
        phoneNumber: String,
        category: String,
        subCategory: String,
        description: String,
        location: String,
        assignedPa: String,
        priority: String,
        entryType: String = "ISSUE"
    ) {
        viewModelScope.launch {
            repository.createTicket(
                customTicketNumber = ticketNumber.trim(),
                citizenName = citizenName.trim(),
                phoneNumber = phoneNumber.trim(),
                category = category,
                subCategory = subCategory.trim(),
                description = description.trim(),
                location = location.trim(),
                assignedPa = assignedPa,
                priority = priority,
                entryType = entryType
            )
            _showRaiseTicketDialog.value = false
            _notificationBanner.value = "Notification: Issue/Report saved successfully for Ticket ${ticketNumber.ifBlank { "T-101" }}."
        }
    }

    fun clearAllDashboardContent() {
        viewModelScope.launch {
            repository.clearAllData()
            _selectedTicket.value = null
        }
    }
}
