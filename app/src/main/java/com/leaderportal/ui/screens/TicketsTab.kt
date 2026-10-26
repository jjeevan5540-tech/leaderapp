package com.leaderportal.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leaderportal.data.model.TicketEntity
import com.leaderportal.ui.components.TicketCardItem
import com.leaderportal.ui.components.TicketTableHeader
import com.leaderportal.ui.components.TicketTableRow
import com.leaderportal.ui.theme.BrandNavy
import com.leaderportal.ui.theme.IssueBlue
import com.leaderportal.ui.theme.LeaderGold
import com.leaderportal.ui.theme.LeaderNavy
import com.leaderportal.ui.theme.NeutralDark
import com.leaderportal.ui.theme.NeutralMedium
import com.leaderportal.ui.theme.PersonalPurple
import com.leaderportal.ui.theme.SolvedGreen
import com.leaderportal.ui.theme.SurfaceBorder
import com.leaderportal.ui.theme.UnsolvedRed
import com.leaderportal.ui.viewmodel.CategoryFilter
import com.leaderportal.ui.viewmodel.DateFilter
import com.leaderportal.ui.viewmodel.SortOrder
import com.leaderportal.ui.viewmodel.StatusFilter
import com.leaderportal.ui.viewmodel.ViewDisplayMode

@Composable
fun TicketsTab(
    tickets: List<TicketEntity>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    categoryFilter: CategoryFilter,
    onCategoryFilterChange: (CategoryFilter) -> Unit,
    statusFilter: StatusFilter,
    onStatusFilterChange: (StatusFilter) -> Unit,
    dateFilter: DateFilter,
    onDateFilterChange: (DateFilter) -> Unit,
    paFilter: String,
    availablePas: List<String>,
    onPaFilterChange: (String) -> Unit,
    sortOrder: SortOrder,
    onSortOrderChange: (SortOrder) -> Unit,
    viewMode: ViewDisplayMode,
    onViewModeChange: (ViewDisplayMode) -> Unit,
    onViewTicket: (TicketEntity) -> Unit,
    onToggleStatus: (TicketEntity) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var paMenuExpanded by remember { mutableStateOf(false) }
    var showExtendedFilters by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar (Requirement: Search bar: Name / Phone Number)
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            placeholder = { Text("Search by Name or Phone Number...", fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = LeaderNavy,
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = NeutralMedium,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ticket_search_bar"),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = LeaderNavy,
                unfocusedBorderColor = SurfaceBorder,
                focusedContainerColor = Color.White,
                unfocusedContainerColor = Color.White
            )
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Row of Primary Filters & Toggles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category Filter Chips
            CategoryFilter.entries.forEach { cat ->
                val isSelected = categoryFilter == cat
                FilterChip(
                    selected = isSelected,
                    onClick = { onCategoryFilterChange(cat) },
                    label = {
                        Text(
                            text = cat.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (cat == CategoryFilter.PERSONAL) PersonalPurple.copy(alpha = 0.15f)
                        else if (cat == CategoryFilter.ISSUE) IssueBlue.copy(alpha = 0.15f)
                        else LeaderNavy.copy(alpha = 0.12f),
                        selectedLabelColor = if (cat == CategoryFilter.PERSONAL) PersonalPurple
                        else if (cat == CategoryFilter.ISSUE) IssueBlue
                        else LeaderNavy
                    )
                )
            }

            // Status Filter Chips
            StatusFilter.entries.forEach { stat ->
                val isSelected = statusFilter == stat
                FilterChip(
                    selected = isSelected,
                    onClick = { onStatusFilterChange(stat) },
                    label = {
                        Text(
                            text = stat.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = if (stat == StatusFilter.SOLVED) SolvedGreen.copy(alpha = 0.15f)
                        else if (stat == StatusFilter.UNSOLVED) UnsolvedRed.copy(alpha = 0.15f)
                        else LeaderNavy.copy(alpha = 0.12f),
                        selectedLabelColor = if (stat == StatusFilter.SOLVED) SolvedGreen
                        else if (stat == StatusFilter.UNSOLVED) UnsolvedRed
                        else LeaderNavy
                    )
                )
            }
        }

        // Second control row: Date Range, PA Filter, Sort, View mode toggle
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Sort Menu Button (Requirement: Sortable by Date & Time (newest first, default))
                Box {
                    OutlinedButton(
                        onClick = { sortMenuExpanded = true },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Sort,
                            contentDescription = null,
                            modifier = Modifier.size(15.dp),
                            tint = BrandNavy
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = when (sortOrder) {
                                SortOrder.DATE_DESC -> "Date (Newest)"
                                SortOrder.DATE_ASC -> "Date (Oldest)"
                                SortOrder.ID_ASC -> "ID (Asc)"
                                SortOrder.ID_DESC -> "ID (Desc)"
                                SortOrder.STATUS -> "Unsolved 1st"
                            },
                            fontSize = 11.sp,
                            color = LeaderNavy,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    DropdownMenu(
                        expanded = sortMenuExpanded,
                        onDismissRequest = { sortMenuExpanded = false }
                    ) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = order.label,
                                        fontSize = 13.sp,
                                        fontWeight = if (sortOrder == order) FontWeight.Bold else FontWeight.Normal,
                                        color = if (sortOrder == order) LeaderNavy else NeutralDark
                                    )
                                },
                                onClick = {
                                    onSortOrderChange(order)
                                    sortMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // PA Filter Menu Button (Requirement: Filters: PA name)
                Box {
                    OutlinedButton(
                        onClick = { paMenuExpanded = true },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (paFilter == "All") "PA: All" else "PA: $paFilter",
                            fontSize = 11.sp,
                            color = LeaderNavy,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    DropdownMenu(
                        expanded = paMenuExpanded,
                        onDismissRequest = { paMenuExpanded = false }
                    ) {
                        availablePas.forEach { pa ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = if (pa == "All") "All Personal Assistants" else pa,
                                        fontSize = 13.sp,
                                        fontWeight = if (paFilter == pa) FontWeight.Bold else FontWeight.Normal,
                                        color = if (paFilter == pa) LeaderNavy else NeutralDark
                                    )
                                },
                                onClick = {
                                    onPaFilterChange(pa)
                                    paMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                // Date filter button
                IconButton(
                    onClick = { showExtendedFilters = !showExtendedFilters },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Date range filter",
                        tint = if (dateFilter != DateFilter.ALL) LeaderGold else LeaderNavy,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // View Mode Toggle (Table View vs Card View)
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
            ) {
                Row(modifier = Modifier.padding(2.dp)) {
                    IconButton(
                        onClick = { onViewModeChange(ViewDisplayMode.CARDS) },
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (viewMode == ViewDisplayMode.CARDS) LeaderNavy.copy(alpha = 0.12f) else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.GridView,
                            contentDescription = "Cards View",
                            tint = if (viewMode == ViewDisplayMode.CARDS) LeaderNavy else NeutralMedium,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = { onViewModeChange(ViewDisplayMode.TABLE) },
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (viewMode == ViewDisplayMode.TABLE) LeaderNavy.copy(alpha = 0.12f) else Color.Transparent)
                    ) {
                        Icon(
                            imageVector = Icons.Default.TableChart,
                            contentDescription = "Table View",
                            tint = if (viewMode == ViewDisplayMode.TABLE) LeaderNavy else NeutralMedium,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Extended Date Range Filter Bar
        AnimatedVisibility(visible = showExtendedFilters) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DateFilter.entries.forEach { df ->
                    val isSelected = dateFilter == df
                    FilterChip(
                        selected = isSelected,
                        onClick = { onDateFilterChange(df) },
                        label = { Text(df.label, fontSize = 11.sp) }
                    )
                }
            }
        }

        // Active ticket count header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${tickets.size} Tickets Found",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = NeutralDark
            )

            if (searchQuery.isNotEmpty() || categoryFilter != CategoryFilter.ALL || statusFilter != StatusFilter.ALL || dateFilter != DateFilter.ALL || paFilter != "All") {
                TextButton(
                    onClick = {
                        onSearchQueryChange("")
                        onCategoryFilterChange(CategoryFilter.ALL)
                        onStatusFilterChange(StatusFilter.ALL)
                        onDateFilterChange(DateFilter.ALL)
                        onPaFilterChange("All")
                    },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text("Reset Filters", fontSize = 11.sp, color = UnsolvedRed, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Content Area: Empty State OR List (Table View / Card View)
        if (tickets.isEmpty()) {
            val isFiltering = searchQuery.isNotEmpty() || categoryFilter != CategoryFilter.ALL || statusFilter != StatusFilter.ALL || dateFilter != DateFilter.ALL || paFilter != "All"
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (isFiltering) Icons.Default.FilterList else Icons.AutoMirrored.Filled.Assignment,
                        contentDescription = null,
                        tint = NeutralDark.copy(alpha = 0.5f),
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (isFiltering) "No matching tickets found" else "Dashboard has no tickets",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = LeaderNavy
                    )
                    Text(
                        text = if (isFiltering) "Try clearing search keywords or active filters" else "No tickets recorded.",
                        fontSize = 12.sp,
                        color = NeutralDark,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        } else if (viewMode == ViewDisplayMode.TABLE) {
            // Table View: Horizontal scrollable table container matching all requested columns
            val horizontalScrollState = rememberScrollState()
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.horizontalScroll(horizontalScrollState)) {
                    TicketTableHeader()
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(tickets, key = { it.id }) { ticket ->
                            TicketTableRow(
                                ticket = ticket,
                                onViewClick = { onViewTicket(ticket) }
                            )
                        }
                    }
                }
            }
        } else {
            // Cards View: Mobile-optimized card list
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tickets, key = { it.id }) { ticket ->
                    TicketCardItem(
                        ticket = ticket,
                        onViewClick = { onViewTicket(ticket) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }
        }
    }
}
