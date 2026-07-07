package com.secretsafe.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.fragment.app.FragmentActivity
import com.secretsafe.R
import com.secretsafe.ui.theme.DarkBg
import com.secretsafe.ui.theme.DarkCard
import com.secretsafe.utils.BiometricHelper

@Composable
fun CreatePinScreen(onPinCreated: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var isConfirmStage by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher),
            contentDescription = "Lock Logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(100.dp).padding(bottom = 16.dp)
        )

        Text(
            text = if (!isConfirmStage) "Create App PIN" else "Confirm App PIN",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = if (!isConfirmStage) "Set a 6-digit security PIN to secure this app" else "Re-enter your 6-digit PIN",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        if (errorMsg.isNotEmpty()) {
            Text(
                text = errorMsg,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Indicator dots
        val activeLen = if (!isConfirmStage) pin.length else confirmPin.length
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 48.dp)
        ) {
            for (i in 1..6) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (i <= activeLen) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                        )
                )
            }
        }

        // Keypad Layout
        PinKeypad(
            onDigitClick = { digit ->
                if (!isConfirmStage) {
                    if (pin.length < 6) {
                        pin += digit
                        if (pin.length == 6) {
                            isConfirmStage = true
                            errorMsg = ""
                        }
                    }
                } else {
                    if (confirmPin.length < 6) {
                        confirmPin += digit
                        if (confirmPin.length == 6) {
                            if (pin == confirmPin) {
                                onPinCreated(pin)
                            } else {
                                confirmPin = ""
                                isConfirmStage = false
                                pin = ""
                                errorMsg = "PINs do not match. Start over."
                            }
                        }
                    }
                }
            },
            onDeleteClick = {
                if (!isConfirmStage) {
                    if (pin.isNotEmpty()) pin = pin.dropLast(1)
                } else {
                    if (confirmPin.isNotEmpty()) confirmPin = confirmPin.dropLast(1)
                    else {
                        isConfirmStage = false
                        errorMsg = ""
                    }
                }
            }
        )
    }
}

@Composable
fun PinLockScreen(savedPin: String, onSuccess: () -> Unit, onSignOut: () -> Unit) {
    var pinAttempt by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

    val context = LocalContext.current
    val activity = context as? FragmentActivity

    val triggerBiometric = {
        if (activity != null) {
            BiometricHelper.authenticate(
                activity = activity,
                title = "Unlock App",
                subtitle = "Verify biometric key to unlock SecretSafe",
                onSuccess = onSuccess,
                onFailure = {
                    // Fail silently so PIN key remains active
                }
            )
        }
    }

    LaunchedEffect(Unit) {
        triggerBiometric()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_launcher),
            contentDescription = "Locked",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(100.dp).padding(bottom = 16.dp)
        )

        Text(
            text = "Enter Security PIN",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Text(
            text = "App locked. Enter PIN or verify fingerprint.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
        )

        if (errorMsg.isNotEmpty()) {
            Text(
                text = errorMsg,
                color = MaterialTheme.colorScheme.error,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 16.dp)
            )
        }

        // Indicator dots
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(bottom = 48.dp)
        ) {
            for (i in 1..6) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            if (i <= pinAttempt.length) MaterialTheme.colorScheme.primary 
                            else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                        )
                )
            }
        }

        PinKeypad(
            onDigitClick = { digit ->
                if (pinAttempt.length < 6) {
                    pinAttempt += digit
                    if (pinAttempt.length == 6) {
                        if (pinAttempt == savedPin) {
                            onSuccess()
                        } else {
                            pinAttempt = ""
                            errorMsg = "Incorrect PIN. Please try again."
                        }
                    }
                }
            },
            onDeleteClick = {
                if (pinAttempt.isNotEmpty()) pinAttempt = pinAttempt.dropLast(1)
            },
            showBiometricOption = true,
            onBiometricClick = { triggerBiometric() }
        )

        Spacer(modifier = Modifier.height(24.dp))

        TextButton(onClick = onSignOut) {
            Text(
                text = "Forgot PIN? Sign Out",
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PinKeypad(
    onDigitClick: (String) -> Unit,
    onDeleteClick: () -> Unit,
    showBiometricOption: Boolean = false,
    onBiometricClick: () -> Unit = {}
) {
    val keys = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("BIO", "0", "DEL")
    )

    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        for (row in keys) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                for (key in row) {
                    when (key) {
                        "BIO" -> {
                            if (showBiometricOption) {
                                IconButton(
                                    onClick = onBiometricClick,
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(DarkCard)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Biometric lock icon",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(68.dp))
                            }
                        }
                        "DEL" -> {
                            IconButton(
                                onClick = onDeleteClick,
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(DarkCard)
                                ) {
                                Text(
                                    text = "DEL",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                        else -> {
                            Button(
                                onClick = { onDigitClick(key) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = DarkCard,
                                    contentColor = MaterialTheme.colorScheme.onBackground
                                ),
                                modifier = Modifier.size(68.dp),
                                shape = CircleShape
                            ) {
                                Text(
                                    text = key,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AppPinVerifyDialog(
    savedPin: String,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    var pinAttempt by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf("") }

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
                    text = "Verify App PIN",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Enter your 6-digit security PIN to confirm",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                if (errorMsg.isNotEmpty()) {
                    Text(
                        text = errorMsg,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    for (i in 1..6) {
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i <= pinAttempt.length) MaterialTheme.colorScheme.primary 
                                    else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)
                                )
                        )
                    }
                }

                PinKeypad(
                    onDigitClick = { digit ->
                        if (pinAttempt.length < 6) {
                            pinAttempt += digit
                            if (pinAttempt.length == 6) {
                                if (pinAttempt == savedPin) {
                                    onSuccess()
                                } else {
                                    pinAttempt = ""
                                    errorMsg = "Incorrect PIN"
                                }
                            }
                        }
                    },
                    onDeleteClick = {
                        if (pinAttempt.isNotEmpty()) pinAttempt = pinAttempt.dropLast(1)
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("CANCEL")
                }
            }
        }
    }
}
