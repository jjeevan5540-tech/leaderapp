package com.leaderportal.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.leaderportal.data.model.TicketEntity
import com.leaderportal.ui.theme.BrandGold
import com.leaderportal.ui.theme.BrandNavy
import com.leaderportal.ui.theme.BrandYellowAccent
import com.leaderportal.ui.theme.LeaderNavy
import com.leaderportal.ui.theme.NeutralDark
import com.leaderportal.ui.theme.NeutralMedium
import com.leaderportal.ui.theme.PriorityHigh
import com.leaderportal.ui.theme.SurfaceBorder
import com.leaderportal.ui.theme.UnsolvedRed

/**
 * Launch phone dialer safely for Contact action
 */
fun contactCitizenByPhone(context: Context, phoneNumber: String) {
    try {
        val intent = Intent(Intent.ACTION_DIAL).apply {
            data = Uri.parse("tel:$phoneNumber")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open dialer for $phoneNumber", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Launch SMS safely
 */
fun contactCitizenBySms(context: Context, phoneNumber: String, citizenName: String, ticketCode: String) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("smsto:$phoneNumber")
            putExtra("sms_body", "Namaste $citizenName garu, regarding your ticket $ticketCode - Leader Portal Office.")
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open SMS for $phoneNumber", Toast.LENGTH_SHORT).show()
    }
}

@Composable
fun CategoryBadge(category: String, modifier: Modifier = Modifier) {
    val isPersonal = category.equals("Personal", ignoreCase = true)
    val bgColor = if (isPersonal) Color(0xFFF3E8FF) else Color(0xFFDBEAFE)
    val textColor = if (isPersonal) Color(0xFF581C87) else Color(0xFF1E40AF)
    val borderColor = if (isPersonal) Color(0xFFC084FC) else Color(0xFF93C5FD)
    val icon = if (isPersonal) Icons.Default.Person else Icons.Default.ReportProblem

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = if (isPersonal) "Personal" else "Issue",
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StatusBadge(isSolved: Boolean, isCompact: Boolean = false, modifier: Modifier = Modifier) {
    val bgColor = if (isSolved) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
    val textColor = if (isSolved) Color(0xFF15803D) else Color(0xFFB91C1C)
    val borderColor = if (isSolved) Color(0xFF86EFAC) else Color(0xFFFCA5A5)
    val icon = if (isSolved) Icons.Default.CheckCircle else Icons.Default.Error
    val text = if (isCompact) {
        if (isSolved) "S" else "US"
    } else {
        if (isSolved) "Solved" else "Unsolved"
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(13.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

/**
 * Mobile-optimized Card View for Tickets
 */
@Composable
fun TicketCardItem(
    ticket: TicketEntity,
    onViewClick: () -> Unit,
    onToggleStatusClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isPersonal = ticket.category.equals("Personal", ignoreCase = true)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("ticket_item_${ticket.id}")
            .clickable(onClick = onViewClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Row 1: Ticket ID + Category + Status + Priority indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ticket.ticketCode,
                        fontWeight = FontWeight.ExtraBold,
                        color = LeaderNavy,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    ticket.distinctCategories.forEach { cat ->
                        CategoryBadge(category = cat)
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (ticket.priority.equals("High", ignoreCase = true) && !ticket.isSolved) {
                        Surface(
                            shape = CircleShape,
                            color = PriorityHigh.copy(alpha = 0.15f),
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = "HIGH",
                                color = PriorityHigh,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                    StatusBadge(isSolved = ticket.isSolved)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Citizen Name & Phone Number + Reports count
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = ticket.citizenName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = NeutralDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = BrandYellowAccent
                            ) {
                                Text(
                                    text = "${ticket.issueCount} issue${if (ticket.issueCount == 1) "" else "s"} • ${ticket.reportCount} report${if (ticket.reportCount == 1) "" else "s"}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = BrandNavy,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            if (ticket.hasUnreadUpdate) {
                                Spacer(modifier = Modifier.width(4.dp))
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = UnsolvedRed
                                ) {
                                    Text(
                                        text = "NEW",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = "Phone: ${ticket.phoneNumber}",
                        fontSize = 13.sp,
                        color = NeutralDark,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Quick dial button directly in card for convenience
                FilledTonalButton(
                    onClick = { contactCitizenByPhone(context, ticket.phoneNumber) },
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.testTag("contact_btn_${ticket.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "Contact Citizen",
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isPersonal) "Contact" else "Call",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // Description / SubCategory / Address fallback
            Spacer(modifier = Modifier.height(6.dp))
            val hasRealIssue = ticket.description.isNotBlank() && !ticket.description.startsWith("Address:", ignoreCase = true)
            if (hasRealIssue) {
                Text(
                    text = "${ticket.subCategory}: ${ticket.effectiveDescription}",
                    fontSize = 13.sp,
                    color = NeutralDark,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "Address: ${ticket.effectiveAddress}",
                    fontSize = 12.sp,
                    color = NeutralMedium,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "No issue written",
                    fontSize = 11.sp,
                    color = UnsolvedRed,
                    fontWeight = FontWeight.Medium
                )
            }

            // Date & Time + Added by PA
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = ticket.formattedDateTime,
                    fontSize = 11.sp,
                    color = NeutralDark,
                    fontWeight = FontWeight.Medium
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = Color(0xFFEFF6FF),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Added by PA",
                            tint = LeaderNavy,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Added by: ${ticket.addedByPaName}",
                            fontSize = 11.sp,
                            color = LeaderNavy,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = SurfaceBorder
            )

            // Action Row: View Details & Status Badge (Read-only)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // View Details: #CA8A04
                androidx.compose.material3.Button(
                    onClick = onViewClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BrandGold),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    modifier = Modifier.testTag("view_btn_${ticket.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "View Details", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                }

                StatusBadge(isSolved = ticket.isSolved)
            }
        }
    }
}

/**
 * Data Table row for table view matching exact requested columns:
 * Citizen Name | Phone Number | Date & Time | Status | Action
 */
@Composable
fun TicketTableRow(
    ticket: TicketEntity,
    onViewClick: () -> Unit,
    onContactClick: () -> Unit = {},
    onToggleStatusClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Col 1: Submitter Name (weight: 1.3f)
        Column(
            modifier = Modifier.weight(1.3f),
            verticalArrangement = Arrangement.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ticket.citizenName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeutralDark,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (ticket.reportCount > 1) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = BrandYellowAccent,
                    ) {
                        Text(
                            text = "${ticket.reportCount}",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = BrandNavy,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }

        // Col 2: Phone Number (weight: 1.1f)
        Row(
            modifier = Modifier.weight(1.1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (ticket.phoneNumber.isNotBlank()) ticket.phoneNumber else "N/A",
                fontSize = 11.sp,
                color = Color(0xFF0F172A),
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Col 3: Date & Time (weight: 1.1f)
        Text(
            text = ticket.formattedDateTime,
            fontSize = 10.sp,
            color = NeutralDark,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1.1f)
        )

        // Col 4: Status (weight: 0.8f) - Solved or Unsolved
        Box(
            modifier = Modifier.weight(0.8f),
            contentAlignment = Alignment.CenterStart
        ) {
            StatusBadge(isSolved = ticket.isSolved, isCompact = true)
        }

        // Col 5: Action (weight: 0.7f) - View only
        Box(
            modifier = Modifier.weight(0.7f),
            contentAlignment = Alignment.CenterEnd
        ) {
            androidx.compose.material3.Button(
                onClick = onViewClick,
                shape = RoundedCornerShape(6.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BrandGold),
                modifier = Modifier
                    .height(28.dp)
                    .testTag("table_view_btn_${ticket.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Visibility,
                    contentDescription = "View",
                    modifier = Modifier.size(11.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text("View", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
    HorizontalDivider(color = SurfaceBorder.copy(alpha = 0.6f))
}

/**
 * Table header row with screen-fitting weights:
 * Name | Phone | Date & Time | Status | Action
 */
@Composable
fun TicketTableHeader() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(BrandGold)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Name", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1.3f))
        Text("Phone", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1.1f))
        Text("Date & Time", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1.1f))
        Text("Status", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(0.8f))
        Text("Action", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(0.7f))
    }
}
