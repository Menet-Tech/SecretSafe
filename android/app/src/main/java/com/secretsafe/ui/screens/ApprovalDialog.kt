package com.secretsafe.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentActivity
import com.secretsafe.api.WSMessage
import com.secretsafe.service.WSBackgroundService
import com.secretsafe.ui.theme.DarkCard
import com.secretsafe.utils.BiometricHelper

@Composable
fun ApprovalDialog(request: WSMessage) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity

    val sharedPref = remember { context.getSharedPreferences("SecretSafePref", Context.MODE_PRIVATE) }
    val appPin = remember { sharedPref.getString("app_pin", "") ?: "" }

    var showPinFallback by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = {
        // Auto-reject if dismissed
        WSBackgroundService.sendResponse(request.request_id ?: "", false)
    }) {
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
                // Header Row containing Warning Icon
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Alert security icon",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Security Verification",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = "A client host is requesting access to a saved credential.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    modifier = Modifier.padding(bottom = 20.dp)
                )

                // Details Box
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Credential Name:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        Text(request.credential_name ?: "Unknown", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        
                        Text("Username:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        Text(request.username ?: "Unknown", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.onBackground)

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("Requester Host:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
                        Text(request.host ?: "Unknown", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onBackground, fontFamily = FontFamily.Monospace)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            WSBackgroundService.sendResponse(request.request_id ?: "", false)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("REJECT", fontWeight = FontWeight.Bold, maxLines = 1)
                    }

                    Button(
                        onClick = {
                            if (activity != null) {
                                // Request Biometric (strictly Fingerprint/Face)
                                BiometricHelper.authenticate(
                                    activity = activity,
                                    title = "Approve Request",
                                    subtitle = "Confirm credential access approval",
                                    onSuccess = {
                                        WSBackgroundService.sendResponse(request.request_id ?: "", true)
                                    },
                                    onFailure = { err ->
                                        // Fall back to custom 6-digit App PIN verification dialog
                                        showPinFallback = true
                                    }
                                )
                            } else {
                                // Fallback
                                showPinFallback = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.background
                        ),
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Text("APPROVE", fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
        }
    }

    if (showPinFallback) {
        AppPinVerifyDialog(
            savedPin = appPin,
            onDismiss = { showPinFallback = false },
            onSuccess = {
                showPinFallback = false
                WSBackgroundService.sendResponse(request.request_id ?: "", true)
            }
        )
    }
}
