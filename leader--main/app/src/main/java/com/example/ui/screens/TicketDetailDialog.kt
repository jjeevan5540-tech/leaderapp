package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ChatMessageEntity
import com.example.data.model.TicketEntity
import com.example.data.model.TimelineEventEntity
import com.example.ui.components.CategoryBadge
import com.example.ui.components.StatusBadge
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandYellowAccent
import com.example.ui.theme.LeaderNavy
import com.example.ui.theme.NeutralDark
import com.example.ui.theme.SolvedGreen
import com.example.ui.theme.SolvedGreenBg
import com.example.ui.theme.SolvedGreenBorder
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.UnsolvedRed
import com.example.ui.theme.UnsolvedRedBg
import com.example.ui.theme.UnsolvedRedBorder

@Composable
fun TicketDetailDialog(
    ticket: TicketEntity,
    chatMessages: List<ChatMessageEntity> = emptyList(),
    timelineEvents: List<TimelineEventEntity> = emptyList(),
    onDismiss: () -> Unit,
    onToggleStatus: (TicketEntity, resolutionNotes: String?) -> Unit = { _, _ -> },
    onSendMessage: (ticketId: Int, message: String) -> Unit = { _, _ -> },
    onDeleteTicket: ((TicketEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Ticket?") },
            text = {
                Text(
                    "This will delete ticket ${ticket.ticketCode} from the Leader Portal, Firestore database, and Ticket Portal."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        onDeleteTicket?.invoke(ticket)
                        onDismiss()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFDC2626))
                ) {
                    Text("Delete Everywhere", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 10.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Modal Header: #CA8A04
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BrandGold)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = ticket.ticketCode,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        CategoryBadge(category = ticket.category.ifBlank { "Issue" })
                        Spacer(modifier = Modifier.width(4.dp))
                        StatusBadge(isSolved = ticket.isSolved)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (onDeleteTicket != null) {
                            IconButton(
                                onClick = { showDeleteConfirm = true },
                                modifier = Modifier
                                    .size(32.dp)
                                    .testTag("delete_ticket_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete Ticket",
                                    tint = Color(0xFFFCA5A5)
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Scrollable Content
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(scrollState)
                        .padding(16.dp)
                ) {
                    // Citizen Info Card (Read-only, no Call & SMS options)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = ticket.citizenName,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LeaderNavy
                                )
                                if (ticket.reportCount > 1) {
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = BrandYellowAccent
                                    ) {
                                        Text(
                                            text = "${ticket.reportCount} reports",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = BrandNavy,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Submitter / Citizen",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B),
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Phone: ${if (ticket.phoneNumber.isNotBlank()) ticket.phoneNumber else "Not Available"}",
                                fontSize = 14.sp,
                                color = NeutralDark,
                                fontWeight = FontWeight.SemiBold
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 10.dp),
                                color = SurfaceBorder
                            )

                            // Highlighted Added by PA card
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0xFFEFF6FF),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("detail_added_by_pa")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Person,
                                        contentDescription = "Added by PA",
                                        tint = LeaderNavy,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "ADDED BY PERSONAL ASSISTANT",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF1E40AF),
                                            letterSpacing = 0.5.sp
                                        )
                                        Text(
                                            text = ticket.addedByPaName.ifBlank { "PA Assigned" },
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = LeaderNavy
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Schedule,
                                    contentDescription = null,
                                    tint = LeaderNavy,
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Raised on: ${ticket.formattedDateTime}",
                                    fontSize = 12.sp,
                                    color = NeutralDark,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    val addr = ticket.effectiveAddress
                    if (addr.isNotBlank()) {
                        // 1. Address Section Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.LocationOn,
                                        contentDescription = null,
                                        tint = LeaderNavy,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Address",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LeaderNavy
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = addr,
                                    fontSize = 13.sp,
                                    color = NeutralDark,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                    }

                    // 2. Description (Issue only) Section Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Description",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = LeaderNavy
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = ticket.effectiveDescription,
                                fontSize = 13.sp,
                                color = NeutralDark,
                                lineHeight = 18.sp
                            )

                            if (ticket.isSolved && !ticket.resolutionNotes.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(8.dp),
                                    color = SolvedGreenBg,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SolvedGreenBorder)
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(
                                            text = "Resolution Notes:",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = SolvedGreen
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = ticket.resolutionNotes,
                                            fontSize = 12.sp,
                                            color = NeutralDark
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (!ticket.reopenedNotice.isNullOrBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFEF3C7),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B))
                        ) {
                            Text(
                                text = ticket.reopenedNotice,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF92400E),
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    // 3. Reports & Issues Section (Timeline sorted in ascending serial order by issue number)
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Reports & Issues Timeline",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LeaderNavy
                                )
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = BrandYellowAccent
                                ) {
                                    Text(
                                        text = "${ticket.issueCount} Issues • ${ticket.reportCount} Reports",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = BrandNavy,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val visibleReports = ticket.visibleReports // Sorted in ascending order by issueNumber!
                            visibleReports.forEachIndexed { index, report ->
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFFF8FAFC),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp)
                                    ) {
                                        // Header Row: Issue Number (Issue #1, Issue #2, Issue #4...) & Category Badge
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Surface(
                                                    shape = RoundedCornerShape(6.dp),
                                                    color = LeaderNavy
                                                ) {
                                                    Text(
                                                        text = "Issue #${if (report.issueNumber > 0) report.issueNumber else index + 1}",
                                                        fontSize = 12.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                    )
                                                }
                                                if (report.subject.isNotBlank()) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Text(
                                                        text = report.subject,
                                                        fontSize = 13.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = LeaderNavy
                                                    )
                                                }
                                            }
                                            CategoryBadge(category = report.category.ifBlank { "Issue" })
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Added by PA line
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Person,
                                                contentDescription = null,
                                                tint = LeaderNavy,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            val displayPa = if (report.paName.isNotBlank() && !report.paName.equals("PA", ignoreCase = true) && !report.paName.equals("PA Assigned", ignoreCase = true)) {
                                                report.paName
                                            } else {
                                                ticket.addedByPaName.ifBlank { "PA" }
                                            }
                                            Text(
                                                text = "Added by: $displayPa",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = LeaderNavy
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))

                                        // Issue Description / Personal Details - Printed line-by-line with clean spacing
                                        val issueText = report.description.ifBlank { report.subject }
                                        if (issueText.isNotBlank()) {
                                            Text(
                                                text = "Issue Description / Personal Details:",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF64748B)
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))

                                            val detailLines = issueText.lines().flatMap { it.split("; ", " - ") }.map { it.trim() }.filter { it.isNotBlank() }
                                            if (detailLines.size > 1) {
                                                detailLines.forEach { line ->
                                                    Row(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(vertical = 2.dp),
                                                        verticalAlignment = Alignment.Top
                                                    ) {
                                                        Text(
                                                            text = "• ",
                                                            fontSize = 13.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = LeaderNavy
                                                        )
                                                        Text(
                                                            text = line,
                                                            fontSize = 13.sp,
                                                            color = NeutralDark,
                                                            lineHeight = 18.sp
                                                        )
                                                    }
                                                }
                                            } else {
                                                Text(
                                                    text = issueText.trim(),
                                                    fontSize = 13.sp,
                                                    color = NeutralDark,
                                                    lineHeight = 18.sp,
                                                    modifier = Modifier.padding(vertical = 2.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(8.dp))
                                        }

                                        // Date & Time line
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Schedule,
                                                contentDescription = null,
                                                tint = Color(0xFF64748B),
                                                modifier = Modifier.size(13.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = report.formattedDateTime,
                                                fontSize = 11.sp,
                                                color = Color(0xFF64748B),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }

                                if (index < visibleReports.size - 1) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Status Display Banner (View only: Solved or Unsolved)
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = if (ticket.isSolved) SolvedGreenBg else UnsolvedRedBg,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (ticket.isSolved) SolvedGreenBorder else UnsolvedRedBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (ticket.isSolved) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (ticket.isSolved) SolvedGreen else UnsolvedRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (ticket.isSolved) "Status: Solved" else "Status: Unsolved / Pending",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (ticket.isSolved) SolvedGreen else UnsolvedRed
                                )
                            }
                            StatusBadge(isSolved = ticket.isSolved)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }
    }
}
