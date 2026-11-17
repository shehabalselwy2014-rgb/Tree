package com.example.presentation.security

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.domain.security.PinSecurityHelper
import com.example.presentation.components.JuthoorBrandLogo
import kotlinx.coroutines.delay

fun isBiometricHardwareReady(context: Context): Boolean {
    val manager = BiometricManager.from(context)
    val result = manager.canAuthenticate(
        BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
    )
    return result == BiometricManager.BIOMETRIC_SUCCESS
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}

private fun launchBiometricPrompt(
    activity: FragmentActivity,
    onAuthenticated: () -> Unit,
    onErrorMessage: (String) -> Unit,
) {
    val executor = ContextCompat.getMainExecutor(activity)
    val prompt = BiometricPrompt(
        activity,
        executor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                onAuthenticated()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                if (errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON &&
                    errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                    errorCode != BiometricPrompt.ERROR_CANCELED
                ) {
                    onErrorMessage(errString.toString())
                }
            }
        },
    )
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("فتح سجل العائلة — جِذور")
        .setSubtitle("استخدم البصمة أو التعرف الحيوي للوصول الآمن إلى شجرة العائلة")
        .setNegativeButtonText("استخدام رمز PIN")
        .setAllowedAuthenticators(
            BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
        )
        .build()
    prompt.authenticate(promptInfo)
}

@Composable
fun AppLockScreen(
    familyTitle: String,
    biometricEnabled: Boolean,
    onVerifyPin: (String) -> Boolean,
    onBiometricUnlocked: () -> Unit,
) {
    val context = LocalContext.current
    val biometricReady = remember(context) { isBiometricHardwareReady(context) }
    var enteredPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var failedAttempts by remember { mutableIntStateOf(0) }
    var cooldownSeconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(cooldownSeconds) {
        if (cooldownSeconds > 0) {
            delay(1000)
            cooldownSeconds -= 1
        }
    }

    LaunchedEffect(biometricEnabled, biometricReady) {
        if (biometricEnabled && biometricReady) {
            val activity = context.findFragmentActivity()
            if (activity != null) {
                launchBiometricPrompt(
                    activity = activity,
                    onAuthenticated = onBiometricUnlocked,
                    onErrorMessage = { msg -> errorMessage = msg },
                )
            }
        }
    }

    val submitPin: () -> Unit = {
        if (cooldownSeconds == 0 && enteredPin.length >= 4) {
            val ok = onVerifyPin(enteredPin)
            if (ok) {
                enteredPin = ""
                errorMessage = null
                failedAttempts = 0
            } else {
                failedAttempts += 1
                enteredPin = ""
                if (failedAttempts >= 5) {
                    cooldownSeconds = 15
                    errorMessage = "تم إدخال رمز غير صحيح عدة مرات. انتظر 15 ثانية ثم أعد المحاولة."
                } else {
                    errorMessage = "رمز القفل غير صحيح (المحاولة $failedAttempts من 5)."
                }
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize().testTag("app_lock_screen"),
        color = MaterialTheme.colorScheme.background,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Card(
                modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    JuthoorBrandLogo(
                        size = 56.dp,
                        onDarkBackground = false,
                        showBadgeContainer = true,
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = CircleShape,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = "قفل خصوصية العائلة",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(14.dp),
                            )
                            Text(
                                text = "حماية خصوصية العائلة",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }

                    Text(
                        text = familyTitle,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "أدخل رمز القفل (PIN) لفتح سجلات شجرة العائلة",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp),
                        ) {
                            repeat(6) { index ->
                                val filled = index < enteredPin.length
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (filled) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceVariant
                                        )
                                        .border(
                                            width = 1.5.dp,
                                            color = if (filled) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.outline,
                                            shape = CircleShape,
                                        ),
                                )
                            }
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.testTag("lock_error_message"),
                        )
                    }
                    if (cooldownSeconds > 0) {
                        Text(
                            text = "إعادة المحاولة متاحة بعد $cooldownSeconds ثانية",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        val rows = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                        )
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            rows.forEach { rowDigits ->
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    rowDigits.forEach { digit ->
                                        PinDigitButton(
                                            digit = digit,
                                            enabled = cooldownSeconds == 0 && enteredPin.length < 6,
                                            onClick = {
                                                errorMessage = null
                                                enteredPin += digit
                                            },
                                        )
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        if (enteredPin.isNotEmpty()) {
                                            enteredPin = enteredPin.dropLast(1)
                                        }
                                    },
                                    enabled = cooldownSeconds == 0 && enteredPin.isNotEmpty(),
                                    modifier = Modifier.size(56.dp).testTag("pin_key_backspace"),
                                    shape = CircleShape,
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                                        contentDescription = "حذف رقم",
                                    )
                                }
                                PinDigitButton(
                                    digit = "0",
                                    enabled = cooldownSeconds == 0 && enteredPin.length < 6,
                                    onClick = {
                                        errorMessage = null
                                        enteredPin += "0"
                                    },
                                )
                                Button(
                                    onClick = submitPin,
                                    enabled = cooldownSeconds == 0 && enteredPin.length >= 4,
                                    modifier = Modifier.size(56.dp).testTag("pin_key_submit"),
                                    shape = CircleShape,
                                ) {
                                    Text("دخول", style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }

                    if (biometricEnabled && biometricReady) {
                        Spacer(modifier = Modifier.height(4.dp))
                        FilledTonalButton(
                            onClick = {
                                val activity = context.findFragmentActivity()
                                if (activity != null) {
                                    launchBiometricPrompt(
                                        activity = activity,
                                        onAuthenticated = onBiometricUnlocked,
                                        onErrorMessage = { msg -> errorMessage = msg },
                                    )
                                } else {
                                    errorMessage = "المصادقة الحيوية غير متاحة في هذا السياق."
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .minimumInteractiveComponentSize()
                                .testTag("lock_biometric_button"),
                        ) {
                            Icon(Icons.Filled.Fingerprint, contentDescription = null)
                            Text(" فتح بالبصمة / المصادقة الحيوية")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PinDigitButton(
    digit: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        modifier = Modifier.size(56.dp).testTag("pin_key_$digit"),
    ) {
        Text(
            text = digit,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun PinSetupDialog(
    isUpdatingExisting: Boolean,
    onDismiss: () -> Unit,
    onConfirmPin: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isUpdatingExisting) "تغيير رمز القفل (PIN)" else "تعيين رمز قفل الخصوصية (PIN)")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "أدخل رمزاً رقمياً من 4 إلى 6 أرقام لحماية بيانات شجرة العائلة محلياً.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { raw ->
                        if (raw.length <= 6 && raw.all { it.isDigit() }) {
                            pin = raw
                            validationError = null
                        }
                    },
                    label = { Text("رمز القفل الجديد (4 - 6 أرقام)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().testTag("pin_setup_input"),
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { raw ->
                        if (raw.length <= 6 && raw.all { it.isDigit() }) {
                            confirmPin = raw
                            validationError = null
                        }
                    },
                    label = { Text("تأكيد رمز القفل") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth().testTag("pin_setup_confirm_input"),
                )
                validationError?.let { msg ->
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val formatErr = PinSecurityHelper.validatePinFormat(pin)
                    if (formatErr != null) {
                        validationError = formatErr
                    } else if (pin != confirmPin) {
                        validationError = "رمزا القفل غير متطابقين."
                    } else {
                        onConfirmPin(pin)
                    }
                },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("pin_setup_save_button"),
            ) {
                Text("حفظ الرمز")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.minimumInteractiveComponentSize(),
            ) {
                Text("إلغاء")
            }
        },
    )
}
