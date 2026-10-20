package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoMode
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.PaUserEntity
import com.example.ui.theme.BrandGold
import com.example.ui.theme.BrandNavy
import com.example.ui.theme.BrandYellowAccent
import com.example.ui.theme.LeaderGold
import com.example.ui.theme.LeaderGoldLight
import com.example.ui.theme.LeaderNavy
import com.example.ui.theme.LeaderNavyDark
import com.example.ui.theme.NeutralDark
import com.example.ui.theme.NeutralMedium
import com.example.ui.theme.SolvedGreen
import com.example.ui.theme.SolvedGreenBg
import com.example.ui.theme.SurfaceBorder
import com.example.ui.theme.UnsolvedRed
import com.example.ui.theme.UnsolvedRedBg
import kotlin.random.Random

@Composable
fun PaCredentialsDialog(
    paList: List<PaUserEntity>,
    onDismiss: () -> Unit,
    onSavePaCredential: (paName: String, phone: String, pass: String, designation: String, status: String) -> Unit,
    onDeletePaCredential: (PaUserEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = View PA List, 1 = Create New PA Login

    // Form states
    var paNameInput by remember { mutableStateOf("") }
    var phoneInput by remember { mutableStateOf("") }
    var passwordInput by remember { mutableStateOf("") }
    var designationInput by remember { mutableStateOf("Personal Assistant") }
    var statusInput by remember { mutableStateOf("Active") }
    var formError by remember { mutableStateOf<String?>(null) }
    var successToast by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    var paToDelete by remember { mutableStateOf<PaUserEntity?>(null) }
    val clipboardManager = LocalClipboardManager.current

    // Auto-generate strong password helper
    fun generateRandomPassword(): String {
        val chars = "abcdefghijklmnopqrstuvwxyz1234567890"
        return (1..8)
            .map { chars[Random.nextInt(chars.length)] }
            .joinToString("")
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.9f)
                .testTag("pa_credentials_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = Color.White,
            shadowElevation = 16.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Modal Header: #CA8A04
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(BrandGold)
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("pa_dialog_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.2f))
                                .border(1.dp, Color.White, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Key,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "PA Portal Logins & Credentials",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Issue & manage PA login credentials",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f)
                            )
                        }
                    }
                }

                // Tab Switcher
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFFF8FAFC),
                    contentColor = BrandNavy,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            height = 3.dp,
                            color = BrandGold
                        )
                    }
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        modifier = Modifier.testTag("pa_tab_list"),
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "PA Accounts",
                                    fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = BrandYellowAccent // Counter: #F5C542
                                ) {
                                    Text(
                                        text = "${paList.size}",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = BrandNavy, // #0F1F4D
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    )

                    Tab(
                        selected = selectedTab == 1,
                        onClick = {
                            selectedTab = 1
                            formError = null
                        },
                        modifier = Modifier.testTag("pa_tab_add"),
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Issue New PA Login",
                                    fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    )
                }

                // Toast Notification Banner (Toast bg: #0F1F4D)
                AnimatedVisibility(visible = successToast != null) {
                    successToast?.let { msg ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(BrandNavy)
                                .padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = BrandYellowAccent,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = msg,
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { successToast = null },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = BrandYellowAccent
                                )
                            }
                        }
                    }
                }

                // Content View based on Selected Tab
                Box(modifier = Modifier.weight(1f)) {
                    if (selectedTab == 0) {
                        // PA List View
                        if (paList.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = NeutralMedium,
                                    modifier = Modifier.size(56.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "No PA Credentials Issued Yet",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LeaderNavy
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Click 'Issue New PA Login' above to generate and grant access to a Personal Assistant.",
                                    fontSize = 13.sp,
                                    color = NeutralDark,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(
                                    onClick = { selectedTab = 1 },
                                    colors = ButtonDefaults.buttonColors(containerColor = LeaderNavy)
                                ) {
                                    Icon(imageVector = Icons.Default.Add, contentDescription = null)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Issue First PA Credentials")
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(paList, key = { it.id }) { pa ->
                                    PaCredentialCard(
                                        paUser = pa,
                                        onCopyPhone = {
                                            clipboardManager.setText(AnnotatedString(pa.phoneNumber))
                                            successToast = "Phone ${pa.phoneNumber} copied to clipboard!"
                                        },
                                        onCopyPassword = {
                                            clipboardManager.setText(AnnotatedString(pa.password))
                                            successToast = "Password for ${pa.paName} copied to clipboard!"
                                        },
                                        onEditClick = {
                                            paNameInput = pa.paName
                                            phoneInput = pa.phoneNumber
                                            passwordInput = pa.password
                                            designationInput = pa.designation
                                            statusInput = pa.status
                                            selectedTab = 1
                                        },
                                        onDeleteClick = {
                                            paToDelete = pa
                                        }
                                    )
                                }
                            }
                        }
                    } else {
                        // Create / Edit PA Login Form
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item {
                                Text(
                                    text = "Issue or Update PA Access",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = LeaderNavy
                                )
                                Text(
                                    text = "Enter details for the Personal Assistant to grant them access to the PA Portal.",
                                    fontSize = 12.sp,
                                    color = NeutralDark
                                )
                            }

                            // Error banner
                            if (formError != null) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(UnsolvedRedBg)
                                            .border(1.dp, UnsolvedRed, RoundedCornerShape(8.dp))
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Error,
                                            contentDescription = null,
                                            tint = UnsolvedRed,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = formError!!,
                                            color = UnsolvedRed,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }

                            // PA Name Input
                            item {
                                OutlinedTextField(
                                    value = paNameInput,
                                    onValueChange = {
                                        paNameInput = it
                                        formError = null
                                    },
                                    label = { Text("PA Name (e.g. PA Suresh)") },
                                    placeholder = { Text("Enter full name") },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = LeaderNavy)
                                    },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pa_name_input"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LeaderNavy)
                                )
                            }

                            // Phone Number Input
                            item {
                                OutlinedTextField(
                                    value = phoneInput,
                                    onValueChange = {
                                        phoneInput = it
                                        formError = null
                                    },
                                    label = { Text("Login Phone Number") },
                                    placeholder = { Text("10-digit mobile number") },
                                    leadingIcon = {
                                        Icon(imageVector = Icons.Default.Phone, contentDescription = null, tint = LeaderNavy)
                                    },
                                    keyboardOptions = KeyboardOptions(
                                        keyboardType = KeyboardType.Phone,
                                        imeAction = ImeAction.Next
                                    ),
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pa_phone_input"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LeaderNavy)
                                )
                            }

                            // Password Input + Auto Generate Button
                            item {
                                Column {
                                    var passVisible by remember { mutableStateOf(true) }
                                    OutlinedTextField(
                                        value = passwordInput,
                                        onValueChange = {
                                            passwordInput = it
                                            formError = null
                                        },
                                        label = { Text("PA Login Password") },
                                        placeholder = { Text("Enter password") },
                                        leadingIcon = {
                                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = LeaderNavy)
                                        },
                                        trailingIcon = {
                                            IconButton(onClick = { passVisible = !passVisible }) {
                                                Icon(
                                                    imageVector = if (passVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                                    contentDescription = null
                                                )
                                            }
                                        },
                                        visualTransformation = if (passVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                        singleLine = true,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("pa_password_input"),
                                        shape = RoundedCornerShape(10.dp),
                                        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LeaderNavy)
                                    )

                                    Spacer(modifier = Modifier.height(6.dp))

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                passwordInput = generateRandomPassword()
                                                formError = null
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AutoMode,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = LeaderNavy
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = "Auto-Generate Password",
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = LeaderNavy
                                            )
                                        }
                                    }
                                }
                            }

                            // Designation Input
                            item {
                                OutlinedTextField(
                                    value = designationInput,
                                    onValueChange = { designationInput = it },
                                    label = { Text("Role / Designation") },
                                    placeholder = { Text("Personal Assistant / Senior PA") },
                                    singleLine = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("pa_designation_input"),
                                    shape = RoundedCornerShape(10.dp),
                                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = LeaderNavy)
                                )
                            }

                            // Submit Button
                            item {
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        if (paNameInput.isBlank()) {
                                            formError = "Please enter PA Name"
                                            return@Button
                                        }
                                        if (phoneInput.isBlank()) {
                                            formError = "Please enter Phone Number"
                                            return@Button
                                        }
                                        if (passwordInput.isBlank()) {
                                            formError = "Please enter Password"
                                            return@Button
                                        }

                                        isSaving = true
                                        onSavePaCredential(
                                            paNameInput,
                                            phoneInput,
                                            passwordInput,
                                            designationInput,
                                            statusInput
                                        )
                                        isSaving = false

                                        successToast = "Credentials for $paNameInput saved & synced to PA Portal!"
                                        selectedTab = 0 // Return to list view

                                        // Reset form
                                        paNameInput = ""
                                        phoneInput = ""
                                        passwordInput = ""
                                        designationInput = "Personal Assistant"
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("save_pa_btn"),
                                    shape = RoundedCornerShape(10.dp),
                                    // Add button: #CA8A04
                                    colors = ButtonDefaults.buttonColors(containerColor = BrandGold)
                                ) {
                                    if (isSaving) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(20.dp),
                                            color = Color.White
                                        )
                                    } else {
                                        Icon(imageVector = Icons.Default.Key, contentDescription = null, tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "SAVE & SYNC TO PA PORTAL",
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            letterSpacing = 0.5.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    paToDelete?.let { pa ->
        AlertDialog(
            onDismissRequest = { paToDelete = null },
            title = { Text("Revoke PA Credentials?") },
            text = {
                Text("Are you sure you want to delete credentials for '${pa.paName}' (${pa.phoneNumber})? They will no longer be able to access the PA Portal.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeletePaCredential(pa)
                        successToast = "Revoked credentials for ${pa.paName}."
                        paToDelete = null
                    }
                ) {
                    Text("Revoke & Delete", color = UnsolvedRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { paToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PaCredentialCard(
    paUser: PaUserEntity,
    onCopyPhone: () -> Unit,
    onCopyPassword: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row: Avatar, Name, Status Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // PA Avatar: #0F1F4D with PA avatar letter: #F5C542
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(BrandNavy),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = paUser.paName.trim().firstOrNull()?.uppercaseChar()?.toString() ?: "P",
                            color = BrandYellowAccent,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = paUser.paName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = LeaderNavy
                        )
                        Text(
                            text = paUser.designation,
                            fontSize = 11.sp,
                            color = NeutralDark
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = SolvedGreenBg
                ) {
                    Text(
                        text = paUser.status.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = SolvedGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Phone Field Row
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF8FAFC),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            tint = NeutralDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Phone:",
                            fontSize = 12.sp,
                            color = NeutralDark
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = paUser.phoneNumber,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = LeaderNavy
                        )
                    }

                    IconButton(
                        onClick = onCopyPhone,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy Phone",
                            tint = LeaderNavy,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Password Field Row
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFF8FAFC),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = NeutralDark,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Password:",
                            fontSize = 12.sp,
                            color = NeutralDark
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (passwordVisible) paUser.password else "••••••••",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = LeaderNavy
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { passwordVisible = !passwordVisible },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Password Visibility",
                                tint = NeutralDark,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        IconButton(
                            onClick = onCopyPassword,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = "Copy Password",
                                tint = LeaderNavy,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Bottom Actions: Edit / Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onEditClick) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(8.dp))

                TextButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        tint = UnsolvedRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Revoke", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = UnsolvedRed)
                }
            }
        }
    }
}
