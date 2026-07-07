package com.secretsafe.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentActivity
import com.secretsafe.R
import com.secretsafe.api.CredentialItem
import com.secretsafe.api.NetworkClient
import com.secretsafe.service.WSBackgroundService
import com.secretsafe.ui.theme.DarkBg
import com.secretsafe.ui.theme.DarkCard
import com.secretsafe.ui.theme.SuccessGreen
import com.secretsafe.ui.theme.AlertRed
import com.secretsafe.utils.BiometricHelper

@Composable
fun DashboardScreen(token: String, serverUrl: String, onSignOut: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    val sharedPref = remember { context.getSharedPreferences("SecretSafePref", Context.MODE_PRIVATE) }
    val appPin = remember { sharedPref.getString("app_pin", "") ?: "" }

    val isConnected = WSBackgroundService.isConnected.value
    var credentials by remember { mutableStateOf<List<CredentialItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf(false) }

    // State parameters for Reveal Password Biometric / PIN actions
    var showRevealDialog by remember { mutableStateOf(false) }
    var revealingCredName by remember { mutableStateOf("") }
    var decryptedPassword by remember { mutableStateOf("") }
    var revealError by remember { mutableStateOf("") }
    var loadingReveal by remember { mutableStateOf(false) }

    // Fallback PIN parameters
    var showPinFallback by remember { mutableStateOf(false) }
    var pendingRevealCredId by remember { mutableStateOf<Int?>(null) }

    var searchQuery by remember { mutableStateOf("") }
    var viewMode by remember { mutableStateOf("grid") } // "grid" | "list"
    var sortBy by remember { mutableStateOf("name-asc") } // "name-asc" | "name-desc" | "newest" | "oldest"
    var itemsPerPage by remember { mutableStateOf(10) } // 5, 10, 25, 50, 0 (All)
    var currentPage by remember { mutableStateOf(1) }
    var showSortDropdown by remember { mutableStateOf(false) }
    var showPageDropdown by remember { mutableStateOf(false) }

    LaunchedEffect(searchQuery, sortBy, itemsPerPage) {
        currentPage = 1
    }

    val filteredCredentials = remember(credentials, searchQuery, sortBy) {
        credentials.filter { cred ->
            val q = searchQuery.lowercase()
            cred.name.lowercase().contains(q) ||
            cred.username.lowercase().contains(q) ||
            (cred.address?.lowercase()?.contains(q) == true)
        }.sortedWith { a, b ->
            when (sortBy) {
                "name-asc" -> a.name.compareTo(b.name, ignoreCase = true)
                "name-desc" -> b.name.compareTo(a.name, ignoreCase = true)
                "newest" -> b.id.compareTo(a.id)
                "oldest" -> a.id.compareTo(b.id)
                else -> 0
            }
        }
    }

    val totalItems = filteredCredentials.size
    val limit = if (itemsPerPage == 0) totalItems else itemsPerPage
    val totalPages = if (limit == 0) 1 else Math.max(1, (totalItems + limit - 1) / limit)
    val activePage = Math.min(currentPage, totalPages)
    val startIndex = (activePage - 1) * limit
    val paginatedCredentials = remember(filteredCredentials, startIndex, limit) {
        if (filteredCredentials.isEmpty() || startIndex >= filteredCredentials.size) {
            emptyList()
        } else {
            filteredCredentials.subList(startIndex, Math.min(startIndex + limit, filteredCredentials.size))
        }
    }

    val fetch = {
        loading = true
        errorMsg = ""
        NetworkClient.fetchCredentials(token, serverUrl) { result ->
            loading = false
            result.fold(
                onSuccess = { items ->
                    credentials = items
                },
                onFailure = { err ->
                    errorMsg = err.message ?: "Failed to load credentials"
                }
            )
        }
    }

    val triggerReveal: (Int) -> Unit = { credId ->
        revealingCredName = credentials.find { it.id == credId }?.name ?: "Credential"
        showRevealDialog = true
        loadingReveal = true
        revealError = ""
        decryptedPassword = ""
        NetworkClient.retrieveCredential(token, serverUrl, credId) { result ->
            loadingReveal = false
            result.fold(
                onSuccess = { decResp ->
                    decryptedPassword = decResp.password
                },
                onFailure = { err ->
                    revealError = err.message ?: "Failed to retrieve password"
                }
            )
        }
    }

    val handleRevealClick: (CredentialItem) -> Unit = { cred ->
        if (activity != null) {
            BiometricHelper.authenticate(
                activity = activity,
                title = "Reveal Password",
                subtitle = "Authenticate to decrypt password for ${cred.name}",
                onSuccess = {
                    triggerReveal(cred.id)
                },
                onFailure = { err ->
                    pendingRevealCredId = cred.id
                    showPinFallback = true
                }
            )
        } else {
            pendingRevealCredId = cred.id
            showPinFallback = true
        }
    }

    LaunchedEffect(Unit) {
        fetch()
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.background,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Credential button")
            }
        },
        containerColor = DarkBg
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg)
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            // Top Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_launcher),
                        contentDescription = "Shield Logo",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "SecretSafe",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "App Settings",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = { fetch() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Credentials",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onSignOut,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.12f),
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text("Sign Out", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Connection status card
            Card(
                colors = CardDefaults.cardColors(containerColor = DarkCard),
                modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp),
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(if (isConnected) SuccessGreen else AlertRed)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = if (isConnected) "SHIELD ACTIVE" else "SHIELD INACTIVE",
                            fontWeight = FontWeight.Bold,
                            color = if (isConnected) SuccessGreen else AlertRed,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isConnected) "Listening for real-time approvals" else "Check backend connectivity",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Text(
                text = "Your Credentials",
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(bottom = 12.dp),
                color = MaterialTheme.colorScheme.onBackground
            )

            // Controls Bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                // Search Input
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search credentials...", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.1f),
                        focusedContainerColor = DarkCard,
                        unfocusedContainerColor = DarkCard
                    ),
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Selectors Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // Sort selector
                        Box {
                            Button(
                                onClick = { showSortDropdown = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DarkCard,
                                    contentColor = MaterialTheme.colorScheme.onBackground
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = "Sort: " + when (sortBy) {
                                        "name-asc" -> "A-Z"
                                        "name-desc" -> "Z-A"
                                        "newest" -> "Newest"
                                        "oldest" -> "Oldest"
                                        else -> ""
                                    },
                                    fontSize = 11.sp
                                )
                            }
                            
                            DropdownMenu(
                                expanded = showSortDropdown,
                                onDismissRequest = { showSortDropdown = false },
                                modifier = Modifier.background(DarkCard)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Name (A-Z)", color = MaterialTheme.colorScheme.onBackground) },
                                    onClick = { sortBy = "name-asc"; showSortDropdown = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Name (Z-A)", color = MaterialTheme.colorScheme.onBackground) },
                                    onClick = { sortBy = "name-desc"; showSortDropdown = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Newest", color = MaterialTheme.colorScheme.onBackground) },
                                    onClick = { sortBy = "newest"; showSortDropdown = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("Oldest", color = MaterialTheme.colorScheme.onBackground) },
                                    onClick = { sortBy = "oldest"; showSortDropdown = false }
                                )
                            }
                        }

                        // Page Limit Selector
                        Box {
                            Button(
                                onClick = { showPageDropdown = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DarkCard,
                                    contentColor = MaterialTheme.colorScheme.onBackground
                                ),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Text(
                                    text = "Show: " + if (itemsPerPage == 0) "All" else "$itemsPerPage Items",
                                    fontSize = 11.sp
                                )
                            }

                            DropdownMenu(
                                expanded = showPageDropdown,
                                onDismissRequest = { showPageDropdown = false },
                                modifier = Modifier.background(DarkCard)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("5 Items", color = MaterialTheme.colorScheme.onBackground) },
                                    onClick = { itemsPerPage = 5; showPageDropdown = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("10 Items", color = MaterialTheme.colorScheme.onBackground) },
                                    onClick = { itemsPerPage = 10; showPageDropdown = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("25 Items", color = MaterialTheme.colorScheme.onBackground) },
                                    onClick = { itemsPerPage = 25; showPageDropdown = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("50 Items", color = MaterialTheme.colorScheme.onBackground) },
                                    onClick = { itemsPerPage = 50; showPageDropdown = false }
                                )
                                DropdownMenuItem(
                                    text = { Text("All", color = MaterialTheme.colorScheme.onBackground) },
                                    onClick = { itemsPerPage = 0; showPageDropdown = false }
                                )
                            }
                        }
                    }

                    // View Mode Toggle (Grid vs List Detail)
                    Row(
                        modifier = Modifier
                            .background(DarkCard, shape = RoundedCornerShape(8.dp))
                            .padding(2.dp)
                            .height(36.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { viewMode = "grid" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (viewMode == "grid") MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (viewMode == "grid") MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Text("Grid", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Button(
                            onClick = { viewMode = "list" },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (viewMode == "list") MaterialTheme.colorScheme.primary else Color.Transparent,
                                contentColor = if (viewMode == "list") MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                            ),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            Text("List", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            if (loading) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (errorMsg.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Warning error icon",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = errorMsg, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                    }
                }
            } else if (credentials.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Empty secure vault icon",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Vault is empty", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                    }
                }
            } else if (filteredCredentials.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "No results icon",
                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "No matching credentials found", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                    }
                }
            } else {
                if (viewMode == "grid") {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(paginatedCredentials) { cred ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkCard),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = "Lock indicator icon",
                                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = cred.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 16.sp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = "User profile icon",
                                            tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = cred.username,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                    if (!cred.address.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "Host: ${cred.address}",
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                                            fontFamily = FontFamily.Monospace,
                                            modifier = Modifier.padding(start = 20.dp)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Button(
                                        onClick = { handleRevealClick(cred) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            contentColor = MaterialTheme.colorScheme.primary
                                        ),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.padding(start = 20.dp)
                                    ) {
                                        Icon(Icons.Default.Lock, contentDescription = "Reveal", modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Reveal Password", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(paginatedCredentials) { cred ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = DarkCard),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Text(
                                            text = cred.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.primary,
                                            maxLines = 1
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = cred.username,
                                            fontSize = 11.sp,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                                            maxLines = 1
                                        )
                                        if (!cred.address.isNullOrBlank()) {
                                            Text(
                                                text = cred.address,
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                                                maxLines = 1
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = { handleRevealClick(cred) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                            contentColor = MaterialTheme.colorScheme.primary
                                        ),
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Reveal", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }

                // Pagination Controls
                if (itemsPerPage > 0 && totalPages > 1) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Page $activePage of $totalPages",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { currentPage = Math.max(currentPage - 1, 1) },
                                enabled = activePage > 1,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DarkCard,
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Prev", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            Button(
                                onClick = { currentPage = Math.min(currentPage + 1, totalPages) },
                                enabled = activePage < totalPages,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DarkCard,
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Next", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddCredentialDialog(
            token = token,
            serverUrl = serverUrl,
            onDismiss = { showAddDialog = false },
            onSuccess = {
                showAddDialog = false
                fetch()
            }
        )
    }

    if (showRevealDialog) {
        RevealPasswordDialog(
            title = revealingCredName,
            decryptedPassword = decryptedPassword,
            error = revealError,
            loading = loadingReveal,
            onDismiss = { showRevealDialog = false }
        )
    }

    if (showPinFallback) {
        AppPinVerifyDialog(
            savedPin = appPin,
            onDismiss = { showPinFallback = false },
            onSuccess = {
                showPinFallback = false
                pendingRevealCredId?.let { triggerReveal(it) }
            }
        )
    }

    if (showSettingsDialog) {
        SettingsDialog(
            serverUrl = serverUrl,
            isConnected = isConnected,
            onDismiss = { showSettingsDialog = false }
        )
    }
}

@Composable
fun RevealPasswordDialog(
    title: String,
    decryptedPassword: String,
    error: String,
    loading: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Decrypted Credentials",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                Text(
                    text = title,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                if (loading) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Awaiting verification response...", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                } else if (error.isNotEmpty()) {
                    Icon(Icons.Default.Warning, contentDescription = "Error icon", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = error, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
                } else if (decryptedPassword.isNotEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("DECRYPTED PASSWORD", fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = decryptedPassword,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                color = MaterialTheme.colorScheme.primary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("CLOSE", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AddCredentialDialog(
    token: String,
    serverUrl: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Add Credential",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (errorMsg.isNotEmpty()) {
                    Text(
                        text = errorMsg,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username / Email") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    singleLine = true
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Website URL (Optional)") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    singleLine = true
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("CANCEL")
                    }

                    Button(
                        onClick = {
                            if (name.isEmpty() || username.isEmpty() || password.isEmpty()) {
                                errorMsg = "Please fill in required fields"
                                return@Button
                            }
                            loading = true
                            errorMsg = ""
                            NetworkClient.addCredential(token, serverUrl, name, username, password, address) { result ->
                                loading = false
                                result.fold(
                                    onSuccess = {
                                        onSuccess()
                                    },
                                    onFailure = { err ->
                                        errorMsg = err.message ?: "Failed to add credential"
                                    }
                                )
                            }
                        },
                        enabled = !loading,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.background
                            )
                        } else {
                            Text("SAVE", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    serverUrl: String,
    isConnected: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val sharedPref = remember { context.getSharedPreferences("SecretSafePref", Context.MODE_PRIVATE) }
    
    var notificationsEnabled by remember { mutableStateOf(sharedPref.getBoolean("notifications_enabled", true)) }
    var notifyConnected by remember { mutableStateOf(sharedPref.getBoolean("notify_connected", true)) }
    var notifyRequest by remember { mutableStateOf(sharedPref.getBoolean("notify_request", true)) }
    var notifyApproveDeny by remember { mutableStateOf(sharedPref.getBoolean("notify_approve_deny", true)) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    text = "Notification Settings",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Info Connection to Security Server
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "SECURITY SERVER INFO",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = serverUrl,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(if (isConnected) SuccessGreen else AlertRed)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isConnected) "Connected" else "Disconnected",
                                fontSize = 11.sp,
                                color = if (isConnected) SuccessGreen else AlertRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Toggles
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Notifications", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Master toggle for all alerts", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                    Switch(
                        checked = notificationsEnabled,
                        onCheckedChange = {
                            notificationsEnabled = it
                            sharedPref.edit().putBoolean("notifications_enabled", it).apply()
                        }
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Server Status Alert", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Show connection alerts in background", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                    Switch(
                        checked = notifyConnected,
                        enabled = notificationsEnabled,
                        onCheckedChange = {
                            notifyConnected = it
                            sharedPref.edit().putBoolean("notify_connected", it).apply()
                        }
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Password Request Alert", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Show alerts when host requests key", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                    Switch(
                        checked = notifyRequest,
                        enabled = notificationsEnabled,
                        onCheckedChange = {
                            notifyRequest = it
                            sharedPref.edit().putBoolean("notify_request", it).apply()
                        }
                    )
                }

                Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Interaction Alert", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                        Text("Confirm when approve or deny is sent", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                    }
                    Switch(
                        checked = notifyApproveDeny,
                        enabled = notificationsEnabled,
                        onCheckedChange = {
                            notifyApproveDeny = it
                            sharedPref.edit().putBoolean("notify_approve_deny", it).apply()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("DONE", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

