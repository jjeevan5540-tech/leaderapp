package com.leaderportal.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.leaderportal.R
import com.leaderportal.ui.theme.BrandGold
import com.leaderportal.ui.theme.BrandNavy
import com.leaderportal.ui.theme.BrandYellowAccent
import com.leaderportal.ui.theme.UnsolvedRed
import com.leaderportal.ui.viewmodel.LeaderViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderDashboardScreen(
    viewModel: LeaderViewModel,
    modifier: Modifier = Modifier
) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val allTickets by viewModel.allTickets.collectAsStateWithLifecycle()

    val rawSelectedTicket by viewModel.selectedTicket.collectAsStateWithLifecycle()
    // Keep selectedTicket updated with latest live data from allTickets
    val selectedTicket = remember(rawSelectedTicket, allTickets) {
        if (rawSelectedTicket == null) null
        else allTickets.firstOrNull { it.id == rawSelectedTicket?.id } ?: rawSelectedTicket
    }
    val selectedTicketChat by viewModel.selectedTicketChat.collectAsStateWithLifecycle()
    val selectedTicketTimeline by viewModel.selectedTicketTimeline.collectAsStateWithLifecycle()

    val userType by viewModel.userType.collectAsStateWithLifecycle()
    val showPaCredentialsDialog by viewModel.showPaCredentialsDialog.collectAsStateWithLifecycle()
    val allPaUsers by viewModel.allPaUsers.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    val showRaiseTicketDialog by viewModel.showRaiseTicketDialog.collectAsStateWithLifecycle()
    val notificationBanner by viewModel.notificationBanner.collectAsStateWithLifecycle()
    val availablePas by viewModel.availablePas.collectAsStateWithLifecycle()

    var showLogoutConfirm by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_app_logo),
                            contentDescription = null,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Leader Portal",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                },
                actions = {
                    if (userType == "LEADER") {
                        // PA Credentials Management Button
                        IconButton(
                            onClick = { viewModel.setShowPaCredentialsDialog(true) },
                            modifier = Modifier.testTag("dashboard_pa_credentials_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = "PA Logins & Credentials",
                                tint = Color.White
                            )
                        }
                    }

                    IconButton(
                        onClick = { showLogoutConfirm = true },
                        modifier = Modifier.testTag("dashboard_logout_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Logout",
                            tint = Color.White
                        )
                    }
                },
                // Topbar: #CA8A04
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BrandGold)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Notification Banner
            notificationBanner?.let { msg ->
                Surface(
                    color = BrandYellowAccent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = msg,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = BrandNavy,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { viewModel.dismissNotificationBanner() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss",
                                tint = BrandNavy
                            )
                        }
                    }
                }
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.refresh() },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("dashboard_pull_to_refresh")
            ) {
                OverviewTab(
                    leaderUser = currentUser,
                    stats = stats,
                    recentTickets = allTickets,
                    onViewTicket = { ticket -> viewModel.selectTicket(ticket) },
                    onManagePaCredentials = { viewModel.setShowPaCredentialsDialog(true) },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // PA Credentials Dialog
    if (showPaCredentialsDialog) {
        PaCredentialsDialog(
            paList = allPaUsers,
            onDismiss = { viewModel.setShowPaCredentialsDialog(false) },
            onSavePaCredential = { name, phone, pass, desig, status ->
                viewModel.savePaCredential(name, phone, pass, desig, status)
            },
            onDeletePaCredential = { pa ->
                viewModel.deletePaCredential(pa)
            }
        )
    }

    // Ticket Detail Dialog (View only: Leader views ticket details & status)
    selectedTicket?.let { ticket ->
        TicketDetailDialog(
            ticket = ticket,
            chatMessages = selectedTicketChat,
            timelineEvents = selectedTicketTimeline,
            onDismiss = { viewModel.selectTicket(null) },
            onDeleteTicket = { t -> viewModel.deleteTicket(t) }
        )
    }

    // Logout Confirmation Dialog
    if (showLogoutConfirm) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirm = false },
            title = { Text("Logout of Leader Console?") },
            text = { Text("You will return to the phone number login screen.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutConfirm = false
                        viewModel.logout()
                    }
                ) {
                    Text("Logout", color = UnsolvedRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
