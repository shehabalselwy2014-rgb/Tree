package com.example.presentation.cloud

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.example.domain.model.ConflictResolutionChoice
import com.example.domain.model.MultiUserDefaults
import com.example.domain.model.SyncState
import com.example.presentation.JuthoorUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FamilyAccountScreen(
    uiState: JuthoorUiState,
    onNavigateBack: () -> Unit,
    onSignIn: (String, String) -> Unit,
    onSignUp: (String, String, String) -> Unit,
    onSignOut: () -> Unit,
    onCreateCloudFamilySpace: (String, String, String?, Boolean) -> Unit,
    onRefreshFamilies: () -> Unit,
    onSwitchActiveFamily: (String) -> Unit,
    onAcceptInvitation: (String, () -> Unit) -> Unit = { _, _ -> },
    onOpenFamilyMembers: () -> Unit = {},
    onSyncNow: () -> Unit = {},
    onResolveConflict: (String, ConflictResolutionChoice) -> Unit = { _, _ -> },
) {
    var isSignUpMode by rememberSaveable { mutableStateOf(false) }
    var displayName by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var showPassword by rememberSaveable { mutableStateOf(false) }
    var inviteCodeInput by rememberSaveable { mutableStateOf("") }

    var newFamilyName by rememberSaveable(uiState.preferences.familyTitle) {
        mutableStateOf(uiState.preferences.familyTitle)
    }
    var newFamilyMotto by rememberSaveable(uiState.preferences.familyMotto) {
        mutableStateOf(uiState.preferences.familyMotto)
    }
    var selectedFounderId by rememberSaveable {
        mutableStateOf<String?>(uiState.persons.firstOrNull()?.id)
    }
    var adoptLocalTree by rememberSaveable {
        mutableStateOf(uiState.persons.isNotEmpty())
    }

    val hasLocalDefaultRecords = remember(uiState.persons, uiState.familySpaces) {
        uiState.persons.any { it.familyId == MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID } ||
            uiState.familySpaces.any { it.id == MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الحساب السحابي ومساحة العائلة") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("family_account_back"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().widthIn(max = 760.dp).testTag("family_account_list"),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (uiState.isCloudBackendConfigured) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                            },
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("cloud_backend_status_card"),
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = if (uiState.isCloudBackendConfigured) Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                                contentDescription = null,
                                tint = if (uiState.isCloudBackendConfigured) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSecondaryContainer
                                },
                            )
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = if (uiState.isCloudBackendConfigured) {
                                        "خادم جِذور السحابي (Supabase) متصل ومهيأ"
                                    } else {
                                        "الوضع المحلي نشط (الخادم السحابي غير مهيأ بعد)"
                                    },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = if (uiState.isCloudBackendConfigured) {
                                        "جميع سجلاتك المحلية آمنة على جهازك، ويمكنك تسجيل الدخول وإنشاء مساحة العائلة السحابية."
                                    } else {
                                        "لتفعيل الحسابات والمزامنة السحابية، أضف قيم SUPABASE_URL و SUPABASE_ANON_KEY في لوحة الأسرار (Secrets) بـ AI Studio. تبقى بيانات الشجرة المحلية محفوظة بالكامل."
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (uiState.authSession == null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth().testTag("auth_card"),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Text(
                                    text = "حساب العائلة السحابي",
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FilterChip(
                                        selected = !isSignUpMode,
                                        onClick = { isSignUpMode = false },
                                        label = { Text("تسجيل الدخول") },
                                        modifier = Modifier.testTag("auth_mode_signin_tab"),
                                    )
                                    FilterChip(
                                        selected = isSignUpMode,
                                        onClick = { isSignUpMode = true },
                                        label = { Text("إنشاء حساب جديد") },
                                        modifier = Modifier.testTag("auth_mode_signup_tab"),
                                    )
                                }

                                if (isSignUpMode) {
                                    OutlinedTextField(
                                        value = displayName,
                                        onValueChange = { displayName = it },
                                        label = { Text("الاسم الكامل لصاحب الحساب") },
                                        singleLine = true,
                                        enabled = !uiState.isAuthBusy,
                                        modifier = Modifier.fillMaxWidth().testTag("auth_display_name_input"),
                                    )
                                }

                                OutlinedTextField(
                                    value = email,
                                    onValueChange = { email = it },
                                    label = { Text("البريد الإلكتروني") },
                                    singleLine = true,
                                    enabled = !uiState.isAuthBusy,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    modifier = Modifier.fillMaxWidth().testTag("auth_email_input"),
                                )

                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("كلمة المرور (6 أحرف فأكثر)") },
                                    singleLine = true,
                                    enabled = !uiState.isAuthBusy,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                    trailingIcon = {
                                        IconButton(
                                            onClick = { showPassword = !showPassword },
                                            modifier = Modifier.minimumInteractiveComponentSize(),
                                        ) {
                                            Icon(
                                                imageVector = if (showPassword) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                contentDescription = if (showPassword) "إخفاء كلمة المرور" else "إظهار كلمة المرور",
                                            )
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().testTag("auth_password_input"),
                                )

                                Button(
                                    onClick = {
                                        if (isSignUpMode) {
                                            onSignUp(email, password, displayName)
                                        } else {
                                            onSignIn(email, password)
                                        }
                                    },
                                    enabled = !uiState.isAuthBusy &&
                                        email.isNotBlank() &&
                                        password.length >= 6 &&
                                        (!isSignUpMode || displayName.isNotBlank()),
                                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("auth_submit_button"),
                                ) {
                                    if (uiState.isAuthBusy) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                        )
                                        Spacer(Modifier.width(8.dp))
                                    } else {
                                        Icon(Icons.Filled.PersonAdd, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    Text(if (isSignUpMode) "إنشاء الحساب" else "تسجيل الدخول")
                                }
                            }
                        }
                    }
                } else {
                    val session = uiState.authSession
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth().testTag("signed_in_account_card"),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Filled.VerifiedUser,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                        Column {
                                            Text(session.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text(
                                                session.email,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    AssistChip(
                                        onClick = {},
                                        label = { Text("بانتظار المزامنة: ${uiState.pendingSyncCount}") },
                                    )
                                }

                                HorizontalDivider()

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = onRefreshFamilies,
                                        enabled = !uiState.isAuthBusy,
                                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("refresh_families_button"),
                                    ) {
                                        Icon(Icons.Filled.Refresh, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("تحديث المساحات")
                                    }
                                    OutlinedButton(
                                        onClick = onSignOut,
                                        enabled = !uiState.isAuthBusy,
                                        modifier = Modifier.weight(1f).minimumInteractiveComponentSize().testTag("sign_out_button"),
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("تسجيل الخروج")
                                    }
                                }

                                val isCloudFamilyActive = uiState.activeFamilySpace != null &&
                                    uiState.activeFamilySpace.id != MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID
                                if (isCloudFamilyActive) {
                                    Button(
                                        onClick = onOpenFamilyMembers,
                                        enabled = !uiState.isAuthBusy,
                                        modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("open_members_invitations_button"),
                                    ) {
                                        Icon(Icons.Filled.GroupAdd, contentDescription = null)
                                        Spacer(Modifier.width(8.dp))
                                        Text("إدارة الدعوات والأعضاء وربط الحسابات")
                                    }
                                }
                            }
                        }
                    }

                    val hasActiveCloudFamily = uiState.activeFamilySpace != null &&
                        uiState.activeFamilySpace.id != MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID
                    if (hasActiveCloudFamily) {
                        item {
                            CloudSyncStatusAndActionsCard(
                                uiState = uiState,
                                onSyncNow = onSyncNow,
                            )
                        }
                        if (uiState.unresolvedSyncConflicts.isNotEmpty()) {
                            item {
                                SyncConflictsCard(
                                    conflicts = uiState.unresolvedSyncConflicts,
                                    isSyncing = uiState.isSyncing,
                                    onResolveConflict = onResolveConflict,
                                )
                            }
                        }
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth().testTag("join_family_card"),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text("الانضمام إلى عائلة برمز دعوة", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    "إذا دعاك مؤسس العائلة أو أحد مديريها، أدخل رمز الدعوة للانضمام وربط حسابك بسجلك في الشجرة.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                OutlinedTextField(
                                    value = inviteCodeInput,
                                    onValueChange = { inviteCodeInput = it },
                                    label = { Text("رمز الدعوة (مثال: JTH-XXXX-XXXX)") },
                                    singleLine = true,
                                    enabled = !uiState.isAuthBusy,
                                    modifier = Modifier.fillMaxWidth().testTag("join_invite_code_input"),
                                )
                                Button(
                                    onClick = {
                                        onAcceptInvitation(inviteCodeInput) {
                                            inviteCodeInput = ""
                                        }
                                    },
                                    enabled = !uiState.isAuthBusy && inviteCodeInput.trim().length >= 4,
                                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("join_invite_submit_button"),
                                ) {
                                    Text("انضمام إلى مساحة العائلة")
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth().testTag("create_family_card"),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Filled.FamilyRestroom, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text("إنشاء مساحة عائلة سحابية", style = MaterialTheme.typography.titleLarge)
                                }
                                Text(
                                    "ينشئ المؤسس مساحة مركزية لعائلته، ثم يدعو أبناءه وأفراد أسرته في المرحلة التالية لإدارة فروعهم.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )

                                OutlinedTextField(
                                    value = newFamilyName,
                                    onValueChange = { newFamilyName = it },
                                    label = { Text("اسم مساحة العائلة") },
                                    singleLine = true,
                                    enabled = !uiState.isAuthBusy,
                                    modifier = Modifier.fillMaxWidth().testTag("create_family_name_input"),
                                )
                                OutlinedTextField(
                                    value = newFamilyMotto,
                                    onValueChange = { newFamilyMotto = it },
                                    label = { Text("شعار أو وصف العائلة") },
                                    singleLine = true,
                                    enabled = !uiState.isAuthBusy,
                                    modifier = Modifier.fillMaxWidth().testTag("create_family_motto_input"),
                                )

                                if (uiState.persons.isNotEmpty()) {
                                    Text("ربط جذر الشجرة / حساب المؤسس بشخص مسجل (اختياري)", style = MaterialTheme.typography.labelLarge)
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        FilterChip(
                                            selected = selectedFounderId == null,
                                            onClick = { selectedFounderId = null },
                                            label = { Text("بدون ربط حالياً") },
                                        )
                                        uiState.persons.take(8).forEach { person ->
                                            FilterChip(
                                                selected = selectedFounderId == person.id,
                                                onClick = { selectedFounderId = person.id },
                                                label = { Text(person.firstName.ifBlank { person.fullName }) },
                                            )
                                        }
                                    }
                                }

                                if (hasLocalDefaultRecords) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("ترحيل الشجرة المحلية الحالية إلى هذه المساحة", style = MaterialTheme.typography.titleSmall)
                                            Text(
                                                "يحافظ على جميع الأفراد والزيجات والصور الحالية ويجهزها للمزامنة السحابية.",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        Switch(
                                            checked = adoptLocalTree,
                                            onCheckedChange = { adoptLocalTree = it },
                                            enabled = !uiState.isAuthBusy,
                                            modifier = Modifier.testTag("adopt_local_tree_switch"),
                                        )
                                    }
                                }

                                Button(
                                    onClick = {
                                        onCreateCloudFamilySpace(
                                            newFamilyName,
                                            newFamilyMotto,
                                            selectedFounderId,
                                            adoptLocalTree && hasLocalDefaultRecords,
                                        )
                                    },
                                    enabled = !uiState.isAuthBusy && newFamilyName.isNotBlank(),
                                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("create_family_submit_button"),
                                ) {
                                    Text("إنشاء مساحة العائلة")
                                }
                            }
                        }
                    }
                }

                if (uiState.familySpaces.isNotEmpty()) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth().testTag("family_spaces_card"),
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Text("مساحات العائلة المسجلة (${uiState.familySpaces.size})", style = MaterialTheme.typography.titleLarge)
                                uiState.familySpaces.forEach { family ->
                                    val isActive = uiState.activeFamilySpace?.id == family.id
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(family.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                            val roleText = if (isActive && uiState.activeMembership != null) {
                                                " • ${uiState.activeMembership.role.arabicLabel}"
                                            } else {
                                                ""
                                            }
                                            val stateLabel = if (family.syncState == SyncState.LOCAL_ONLY) "محلية" else "سحابية (${family.syncState.arabicLabel})"
                                            Text(
                                                text = "$stateLabel$roleText",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                        if (isActive) {
                                            AssistChip(onClick = {}, label = { Text("نشطة") })
                                        } else {
                                            OutlinedButton(
                                                onClick = { onSwitchActiveFamily(family.id) },
                                                modifier = Modifier.minimumInteractiveComponentSize().testTag("switch_family_${family.id}"),
                                            ) {
                                                Text("تفعيل")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.size(76.dp).testTag("family_account_bottom_spacer"))
                }
            }
        }
    }
}
