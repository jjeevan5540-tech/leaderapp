package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TicketEntity
import com.example.ui.components.TicketTableHeader
import com.example.ui.components.TicketTableRow
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandYellowAccent
import com.example.ui.theme.LeaderGold
import com.example.ui.theme.LeaderNavy
import com.example.ui.theme.NeutralDark
import com.example.ui.theme.NeutralMedium
import com.example.ui.theme.PersonalPurple
import com.example.ui.theme.PersonalPurpleBg
import com.example.ui.theme.SolvedGreen
import com.example.ui.theme.SolvedGreenBg
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.UnsolvedRed
import com.example.ui.theme.UnsolvedRedBg
import com.example.ui.viewmodel.DashboardStats
import com.example.ui.viewmodel.LeaderUser

@Composable
fun OverviewTab(
    leaderUser: LeaderUser,
    stats: DashboardStats,
    recentTickets: List<TicketEntity>,
    onViewTicket: (TicketEntity) -> Unit,
    onManagePaCredentials: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(4.dp))
            // Dashboard section title: #0F1F4D
            Text(
                text = "Dashboard",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = BrandNavy
            )
        }

            item {
                // 4 Core Metric Cards
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard(
                            title = "Total Tickets",
                            value = "${stats.totalTickets}",
                            subtitle = "Raised in constituency",
                            icon = Icons.AutoMirrored.Filled.Assignment,
                            iconTint = BrandNavy,
                            bgTint = Color(0xFFF1F5F9),
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Issue Solved",
                            value = "${stats.solvedCount}",
                            subtitle = String.format("%.0f%% Resolution Rate", stats.solvedRate),
                            icon = Icons.Default.CheckCircle,
                            iconTint = SolvedGreen,
                            bgTint = SolvedGreenBg,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        MetricCard(
                            title = "Issue Unsolved",
                            value = "${stats.unsolvedCount}",
                            subtitle = "Pending / in progress",
                            icon = Icons.Default.Error,
                            iconTint = UnsolvedRed,
                            bgTint = UnsolvedRedBg,
                            modifier = Modifier.weight(1f)
                        )
                        MetricCard(
                            title = "Personal / Issue",
                            value = "${stats.personalCount} / ${stats.issueCount}",
                            subtitle = "Personal vs Public issues",
                            icon = Icons.Default.Person,
                            iconTint = PersonalPurple,
                            bgTint = PersonalPurpleBg,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Ticket Table Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                ) {
                    Text(
                        text = "Ticket Table Overview",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandNavy
                    )
                }
            }

            // Ticket Table View
            if (recentTickets.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("No tickets recorded yet", fontSize = 14.sp, color = NeutralDark)
                        }
                    }
                }
            } else {
                // Table View fitting screen size
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("overview_ticket_table"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                        ) {
                            // Column Headers
                            TicketTableHeader()

                            // Rows: only View option is clickable
                            recentTickets.forEach { ticket ->
                                TicketTableRow(
                                    ticket = ticket,
                                    onViewClick = { onViewTicket(ticket) }
                                )
                            }
                        }
                    }
                }

                // Table Counter Chip
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = BrandYellowAccent,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "${recentTickets.size}",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = BrandNavy,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = "tickets in overview",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BrandNavy
                        )
                    }
                }
            }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    bgTint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = LeaderNavy
                )
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(bgTint),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
                color = LeaderNavy
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = NeutralDark,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
