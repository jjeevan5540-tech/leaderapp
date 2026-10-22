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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandYellowAccent
import com.example.ui.theme.IssueBlue
import com.example.ui.theme.LeaderNavy
import com.example.ui.theme.NeutralDark
import com.example.ui.theme.PersonalPurple

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RaiseTicketDialog(
    onDismiss: () -> Unit,
    onSubmit: (
        ticketNumber: String,
        citizenName: String,
        phoneNumber: String,
        category: String,
        subCategory: String,
        description: String,
        location: String,
        assignedPa: String,
        priority: String
    ) -> Unit,
    availablePas: List<String> = emptyList(),
    modifier: Modifier = Modifier
) {
    var ticketNumber by remember { mutableStateOf("") }
    var citizenName by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Personal") } // "Personal" or "Issue"
    var subCategory by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var assignedPa by remember { mutableStateOf("Leader Direct") }
    var priority by remember { mutableStateOf("Medium") }

    var paDropdownExpanded by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }

    val paOptions = remember(availablePas) {
        val filtered = availablePas.filter { it.isNotBlank() && it != "All" }
        if (filtered.isEmpty()) {
            listOf("Leader Direct")
        } else {
            if ("Leader Direct" in filtered) filtered else listOf("Leader Direct") + filtered
        }
    }
    val priorityOptions = listOf("Low", "Medium", "High")

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth(0.94f)
                .verticalScroll(rememberScrollState()),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Modal Header: #CA8A04
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BrandGold)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Raise Ticket / Add Issue",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }

                Column(modifier = Modifier.padding(16.dp)) {
                    // Ticket Number Input (Rule 1: PA enters Ticket Number)
                    OutlinedTextField(
                        value = ticketNumber,
                        onValueChange = {
                            ticketNumber = it
                            errorText = null
                        },
                        label = { Text("Ticket Number *") },
                        placeholder = { Text("e.g. T-101 (Auto-appends if ticket exists)") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("raise_ticket_number"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Category Selection Chips: #0F1F4D heading
                    Text(
                        text = "Category Type",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = BrandNavy
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = category == "Personal",
                            onClick = {
                                category = "Personal"
                                if (subCategory.isEmpty()) subCategory = "Medical Aid"
                            },
                            label = { Text("Personal Request", fontWeight = FontWeight.Bold) },
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PersonalPurple.copy(alpha = 0.15f),
                                selectedLabelColor = PersonalPurple
                            )
                        )

                        FilterChip(
                            selected = category == "Issue",
                            onClick = {
                                category = "Issue"
                                if (subCategory.isEmpty()) subCategory = "Public Infrastructure"
                            },
                            label = { Text("Public Issue", fontWeight = FontWeight.Bold) },
                            colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                                selectedContainerColor = IssueBlue.copy(alpha = 0.15f),
                                selectedLabelColor = IssueBlue
                            )
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Name
                    OutlinedTextField(
                        value = citizenName,
                        onValueChange = {
                            citizenName = it
                            errorText = null
                        },
                        label = { Text("Name *") },
                        placeholder = { Text("e.g. Ramesh Varma") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("raise_citizen_name"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Phone Number
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = {
                            phoneNumber = it
                            errorText = null
                        },
                        label = { Text("Phone Number *") },
                        placeholder = { Text("e.g. 9845123456") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("raise_phone_number"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Subcategory
                    OutlinedTextField(
                        value = subCategory,
                        onValueChange = { subCategory = it },
                        label = { Text("Topic / Subject") },
                        placeholder = { Text(if (category == "Personal") "e.g. Pension, Aid, Recommendation" else "e.g. Street Light, Drainage, Water") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Ward / Location
                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Ward / Colony / Landmark") },
                        placeholder = { Text("e.g. Ward 10, Shivaji Nagar") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Description
                    OutlinedTextField(
                        value = description,
                        onValueChange = {
                            description = it
                            errorText = null
                        },
                        label = { Text("Description / Grievance Details *") },
                        placeholder = { Text("Details of the citizen's request or issue...") },
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("raise_description"),
                        shape = RoundedCornerShape(10.dp)
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Assigned PA Dropdown
                    ExposedDropdownMenuBox(
                        expanded = paDropdownExpanded,
                        onExpandedChange = { paDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = assignedPa,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Assigned PA / Officer") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = paDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        )
                        ExposedDropdownMenu(
                            expanded = paDropdownExpanded,
                            onDismissRequest = { paDropdownExpanded = false }
                        ) {
                            paOptions.forEach { pa ->
                                DropdownMenuItem(
                                    text = { Text(pa) },
                                    onClick = {
                                        assignedPa = pa
                                        paDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Priority
                    Text(
                        text = "Priority Level",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = LeaderNavy
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        priorityOptions.forEach { prio ->
                            FilterChip(
                                selected = priority == prio,
                                onClick = { priority = prio },
                                label = { Text(prio, fontSize = 11.sp) }
                            )
                        }
                    }

                    errorText?.let { err ->
                        Text(
                            text = err,
                            color = Color.Red,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss) {
                            Text("Cancel", color = NeutralDark)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (citizenName.isBlank()) {
                                    errorText = "Please enter name"
                                    return@Button
                                }
                                if (phoneNumber.isBlank()) {
                                    errorText = "Please enter phone number"
                                    return@Button
                                }
                                if (description.isBlank()) {
                                    errorText = "Please enter description"
                                    return@Button
                                }
                                onSubmit(
                                    ticketNumber.ifBlank { "T-101" },
                                    citizenName,
                                    phoneNumber,
                                    category,
                                    subCategory.ifBlank { if (category == "Personal") "Personal Request" else "Public Issue" },
                                    description,
                                    location.ifBlank { "Constituency Area" },
                                    assignedPa,
                                    priority
                                )
                            },
                            shape = RoundedCornerShape(10.dp),
                            // Add button: #CA8A04
                            colors = ButtonDefaults.buttonColors(containerColor = BrandGold),
                            modifier = Modifier.testTag("submit_raise_ticket_btn")
                        ) {
                            Text("Log Ticket / Issue", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}
