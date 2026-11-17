package com.example.presentation.cloud

import android.content.Intent
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.domain.model.FamilyMemberWithProfile
import com.example.domain.model.FamilyRole
import com.example.domain.model.InvitationStatus
import com.example.domain.model.MembershipStatus
import com.example.domain.model.MultiUserDefaults
import com.example.domain.model.Person
import com.example.presentation.JuthoorUiState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FamilyMembersAndInvitationsScreen(
    uiState: JuthoorUiState,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onCreateInvitation: (
        targetPersonId: String?,
        assignedRole: FamilyRole,
        branchRootPersonId: String?,
        maxUses: Int,
        validHours: Int,
    ) -> Unit,
    onDismissGeneratedInvite: () -> Unit,
    onRevokeInvitation: (String) -> Unit,
    onUpdateMemberLinkAndPermissions: (
        memberId: String,
        linkedPersonId: String?,
        branchRootPersonId: String?,
        role: FamilyRole,
        canInvite: Boolean,
        canExport: Boolean,
        canManageMedia: Boolean,
        status: MembershipStatus,
    ) -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var selectedTargetPersonId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedRole by rememberSaveable { mutableStateOf(FamilyRole.BRANCH_MANAGER) }
    var selectedValidHours by rememberSaveable { mutableIntStateOf(72) }
    var selectedMaxUses by rememberSaveable { mutableIntStateOf(1) }
    var editingMember by remember { mutableStateOf<FamilyMemberWithProfile?>(null) }

    val activeFamily = uiState.activeFamilySpace
    val myMembership = uiState.activeMembership
    val canInvite = myMembership != null &&
        myMembership.status == MembershipStatus.ACTIVE &&
        (myMembership.role in listOf(FamilyRole.OWNER, FamilyRole.ADMIN) || myMembership.canInvite)
    val canManageMembers = myMembership != null &&
        myMembership.status == MembershipStatus.ACTIVE &&
        myMembership.role in listOf(FamilyRole.OWNER, FamilyRole.ADMIN)
    val isCloudWorkspace = activeFamily != null && activeFamily.id != MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID

    val personById = remember(uiState.persons) { uiState.persons.associateBy { it.id } }
    val claimedPersonIdsByMember = remember(uiState.activeFamilyMembers) {
        uiState.activeFamilyMembers
            .filter { it.member.status == MembershipStatus.ACTIVE && !it.member.linkedPersonId.isNullOrBlank() }
            .associate { it.member.linkedPersonId!! to it.member.id }
    }
    val availablePersonsForInvite = remember(uiState.persons, claimedPersonIdsByMember) {
        uiState.persons.filter { !claimedPersonIdsByMember.containsKey(it.id) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("الدعوات وأعضاء العائلة") },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.minimumInteractiveComponentSize().testTag("family_members_back"),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                },
                actions = {
                    if (isCloudWorkspace && uiState.authSession != null) {
                        IconButton(
                            onClick = onRefresh,
                            enabled = !uiState.isAuthBusy,
                            modifier = Modifier.minimumInteractiveComponentSize().testTag("refresh_collaboration_button"),
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "تحديث")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(modifier = Modifier.fillMaxSize().widthIn(max = 780.dp)) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = activeFamily?.name ?: "لا توجد مساحة عائلة نشطة",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        val myLinkedPerson = myMembership?.linkedPersonId?.let { personById[it]?.fullName }
                        Text(
                            text = when {
                                !isCloudWorkspace -> "يرجى تفعيل أو إنشاء مساحة عائلة سحابية أولاً لإرسال الدعوات وإدارة الأعضاء."
                                myMembership == null -> "يرجى تحديث المساحات لعرض دورك في هذه العائلة."
                                myLinkedPerson != null -> "دورك: ${myMembership.role.arabicLabel} • حسابك مربوط بـ: $myLinkedPerson"
                                else -> "دورك: ${myMembership.role.arabicLabel} • الحساب غير مربوط بشخص محدد في الشجرة بعد"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                PrimaryTabRow(selectedTabIndex = selectedTab) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("الدعوات (${uiState.activeFamilyInvitations.size})") },
                        modifier = Modifier.testTag("invitations_tab"),
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("الأعضاء والربط (${uiState.activeFamilyMembers.size})") },
                        modifier = Modifier.testTag("members_tab"),
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("collaboration_list"),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (selectedTab == 0) {
                        val generated = uiState.latestGeneratedInvite
                        if (generated != null) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f)),
                                    modifier = Modifier.fillMaxWidth().testTag("generated_invite_code_card"),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
                                            Text("تم توليد رمز الدعوة بنجاح", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        }
                                        Text(
                                            text = "انسخ هذا الرمز وأرسله للشخص المدعو؛ لن يُعرض الرمز الكامل مرة أخرى لأسباب أمنية.",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(
                                            text = generated.plainInviteCode,
                                            style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.testTag("generated_invite_plain_code"),
                                        )
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(
                                                onClick = {
                                                    clipboard.setText(AnnotatedString(generated.plainInviteCode))
                                                },
                                                modifier = Modifier.minimumInteractiveComponentSize().testTag("copy_invite_code_button"),
                                            ) {
                                                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                                Spacer(Modifier.width(6.dp))
                                                Text("نسخ الرمز")
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    val shareBody = "دعوة للانضمام إلى شجرة «${activeFamily?.name.orEmpty()}» في تطبيق جِذور.\nرمز الدعوة: ${generated.plainInviteCode}"
                                                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                        type = "text/plain"
                                                        putExtra(Intent.EXTRA_TEXT, shareBody)
                                                    }
                                                    context.startActivity(Intent.createChooser(sendIntent, "مشاركة رمز الدعوة"))
                                                },
                                                modifier = Modifier.minimumInteractiveComponentSize().testTag("share_invite_code_button"),
                                            ) {
                                                Icon(Icons.Filled.Share, contentDescription = null)
                                                Spacer(Modifier.width(6.dp))
                                                Text("مشاركة")
                                            }
                                            TextButton(
                                                onClick = onDismissGeneratedInvite,
                                                modifier = Modifier.minimumInteractiveComponentSize(),
                                            ) {
                                                Text("إخفاء")
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (isCloudWorkspace && canInvite) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("create_invitation_card"),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Filled.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Text("إنشاء دعوة لعضو جديد", style = MaterialTheme.typography.titleLarge)
                                        }
                                        Text(
                                            "اختر سجل الابن أو الفرد في الشجرة لربط حسابه فور قبوله الدعوة ومنحه صلاحية إدارة فرعه.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )

                                        Text("ربط الدعوة بشخص من الشجرة (اختياري):", style = MaterialTheme.typography.labelLarge)
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            FilterChip(
                                                selected = selectedTargetPersonId == null,
                                                onClick = { selectedTargetPersonId = null },
                                                label = { Text("انضمام عام (يُربط لاحقاً)") },
                                                modifier = Modifier.testTag("invite_target_none"),
                                            )
                                            availablePersonsForInvite.take(12).forEach { person ->
                                                FilterChip(
                                                    selected = selectedTargetPersonId == person.id,
                                                    onClick = { selectedTargetPersonId = person.id },
                                                    label = { Text(person.firstName.ifBlank { person.fullName }) },
                                                    modifier = Modifier.testTag("invite_target_person_${person.id}"),
                                                )
                                            }
                                        }

                                        Text("الصلاحية الممنوحة للعضو:", style = MaterialTheme.typography.labelLarge)
                                        val assignableRoles = buildList {
                                            if (myMembership?.role == FamilyRole.OWNER) add(FamilyRole.ADMIN)
                                            add(FamilyRole.BRANCH_MANAGER)
                                            add(FamilyRole.MEMBER)
                                            add(FamilyRole.RESTRICTED_MEMBER)
                                        }
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            assignableRoles.forEach { role ->
                                                FilterChip(
                                                    selected = selectedRole == role,
                                                    onClick = { selectedRole = role },
                                                    label = { Text(role.arabicLabel) },
                                                    modifier = Modifier.testTag("invite_role_${role.name}"),
                                                )
                                            }
                                        }

                                        Text("مدة الصلاحية وعدد الاستخدامات:", style = MaterialTheme.typography.labelLarge)
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            listOf(24 to "24 ساعة", 72 to "3 أيام", 168 to "7 أيام", 720 to "30 يوماً").forEach { (hours, label) ->
                                                FilterChip(
                                                    selected = selectedValidHours == hours,
                                                    onClick = { selectedValidHours = hours },
                                                    label = { Text(label) },
                                                )
                                            }
                                            listOf(1, 3, 5).forEach { uses ->
                                                FilterChip(
                                                    selected = selectedMaxUses == uses,
                                                    onClick = { selectedMaxUses = uses },
                                                    label = { Text("استخدام: $uses") },
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                onCreateInvitation(
                                                    selectedTargetPersonId,
                                                    selectedRole,
                                                    selectedTargetPersonId,
                                                    selectedMaxUses,
                                                    selectedValidHours,
                                                )
                                            },
                                            enabled = !uiState.isAuthBusy,
                                            modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("create_invitation_submit_button"),
                                        ) {
                                            Text("إنشاء رمز الدعوة")
                                        }
                                    }
                                }
                            }
                        }

                        if (uiState.activeFamilyInvitations.isEmpty()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(Icons.Filled.GroupAdd, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("لا توجد دعوات مسجلة بعد", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "أنشئ دعوة لابنك أو لأحد أفراد الأسرة لربط حسابه الشخصي بفرعه في الشجرة.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        } else {
                            items(uiState.activeFamilyInvitations, key = { it.id }) { inv ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("invitation_item_${inv.id}"),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Text(
                                                text = inv.inviteCodeHint,
                                                style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.Monospace),
                                                fontWeight = FontWeight.Bold,
                                            )
                                            AssistChip(onClick = {}, label = { Text(inv.status.arabicLabel) })
                                        }
                                        val targetName = inv.targetPersonId?.let { personById[it]?.fullName }
                                        Text(
                                            text = "الدور: ${inv.assignedRole.arabicLabel}" +
                                                if (targetName != null) " • مربوطة بـ: $targetName" else " • ربط مفتوح",
                                            style = MaterialTheme.typography.bodySmall,
                                        )
                                        Text(
                                            text = "الاستخدامات: ${inv.usesCount}/${inv.maxUses} • تنتهي في: ${formatDateShort(inv.expiresAt)}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        if (inv.status == InvitationStatus.PENDING && canInvite) {
                                            OutlinedButton(
                                                onClick = { onRevokeInvitation(inv.id) },
                                                enabled = !uiState.isAuthBusy,
                                                modifier = Modifier.minimumInteractiveComponentSize().testTag("revoke_invite_${inv.id}"),
                                            ) {
                                                Text("إلغاء الدعوة")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        if (uiState.activeFamilyMembers.isEmpty()) {
                            item {
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(20.dp).fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(Icons.Filled.Groups, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("لا يوجد أعضاء سحابيون محمّلون بعد", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            "اضغط على زر التحديث بالأعلى لجلب قائمة الأعضاء بعد إنشاء مساحة العائلة.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        } else {
                            items(uiState.activeFamilyMembers, key = { it.member.id }) { entry ->
                                val member = entry.member
                                val linkedPerson = member.linkedPersonId?.let { personById[it] }
                                val branchPerson = member.branchRootPersonId?.let { personById[it] }
                                val editableByMe = canManageMembers && (
                                    myMembership?.role == FamilyRole.OWNER ||
                                        (member.role !in listOf(FamilyRole.OWNER, FamilyRole.ADMIN) || member.userId == uiState.authSession?.userId)
                                    )
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("member_card_${member.id}"),
                                ) {
                                    Column(
                                        modifier = Modifier.padding(14.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.weight(1f),
                                            ) {
                                                Icon(Icons.Filled.Badge, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                                Column {
                                                    Text(entry.displayName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                                    if (entry.email.isNotBlank()) {
                                                        Text(entry.email, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                }
                                            }
                                            AssistChip(onClick = {}, label = { Text(member.role.arabicLabel) })
                                        }

                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.secondary)
                                            Text(
                                                text = if (linkedPerson != null) {
                                                    "مربوط في الشجرة بـ: ${linkedPerson.fullName}"
                                                } else {
                                                    "غير مربوط بسجل شخص في الشجرة بعد"
                                                },
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = if (linkedPerson != null) FontWeight.Medium else FontWeight.Normal,
                                            )
                                        }

                                        if (branchPerson != null && branchPerson.id != linkedPerson?.id) {
                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                Icon(Icons.Filled.PersonPin, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.tertiary)
                                                Text("نطاق الفرع: ${branchPerson.fullName}", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }

                                        val permSummary = buildList {
                                            add("الحالة: ${member.status.arabicLabel}")
                                            if (member.canInvite) add("دعوة")
                                            if (member.canExport) add("تصدير")
                                            if (member.canManageMedia) add("صور")
                                        }.joinToString(" • ")
                                        Text(permSummary, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                                        if (editableByMe) {
                                            OutlinedButton(
                                                onClick = { editingMember = entry },
                                                enabled = !uiState.isAuthBusy,
                                                modifier = Modifier.minimumInteractiveComponentSize().testTag("edit_member_${member.id}"),
                                            ) {
                                                Text("ربط بالشجرة وتعديل الصلاحيات")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item { Spacer(Modifier.height(80.dp).testTag("collaboration_bottom_spacer")) }
                }
            }
        }
    }

    val memberToEdit = editingMember
    if (memberToEdit != null) {
        EditMemberLinkDialog(
            entry = memberToEdit,
            callerRole = myMembership?.role ?: FamilyRole.MEMBER,
            persons = uiState.persons,
            claimedPersonIdsByMember = claimedPersonIdsByMember,
            onDismiss = { editingMember = null },
            onConfirm = { linkedId, branchId, role, canInv, canExp, canMed, status ->
                onUpdateMemberLinkAndPermissions(
                    memberToEdit.member.id,
                    linkedId,
                    branchId,
                    role,
                    canInv,
                    canExp,
                    canMed,
                    status,
                )
                editingMember = null
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditMemberLinkDialog(
    entry: FamilyMemberWithProfile,
    callerRole: FamilyRole,
    persons: List<Person>,
    claimedPersonIdsByMember: Map<String, String>,
    onDismiss: () -> Unit,
    onConfirm: (
        linkedPersonId: String?,
        branchRootPersonId: String?,
        role: FamilyRole,
        canInvite: Boolean,
        canExport: Boolean,
        canManageMedia: Boolean,
        status: MembershipStatus,
    ) -> Unit,
) {
    val member = entry.member
    var linkedPersonId by remember(member.id) { mutableStateOf(member.linkedPersonId) }
    var branchRootPersonId by remember(member.id) { mutableStateOf(member.branchRootPersonId ?: member.linkedPersonId) }
    var role by remember(member.id) { mutableStateOf(member.role) }
    var canInvite by remember(member.id) { mutableStateOf(member.canInvite) }
    var canExport by remember(member.id) { mutableStateOf(member.canExport) }
    var canManageMedia by remember(member.id) { mutableStateOf(member.canManageMedia) }
    var status by remember(member.id) { mutableStateOf(member.status) }

    val availablePersons = remember(persons, claimedPersonIdsByMember, member.id) {
        persons.filter { person ->
            val ownerMemberId = claimedPersonIdsByMember[person.id]
            ownerMemberId == null || ownerMemberId == member.id
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ربط وصلاحيات ${entry.displayName}") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).testTag("member_edit_dialog"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("ربط الحساب بشخص في شجرة العائلة:", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = linkedPersonId == null,
                        onClick = { linkedPersonId = null },
                        label = { Text("بدون ربط") },
                        modifier = Modifier.testTag("link_person_none"),
                    )
                    availablePersons.take(12).forEach { person ->
                        FilterChip(
                            selected = linkedPersonId == person.id,
                            onClick = {
                                linkedPersonId = person.id
                                if (branchRootPersonId == null || branchRootPersonId == member.linkedPersonId) {
                                    branchRootPersonId = person.id
                                }
                            },
                            label = { Text(person.firstName.ifBlank { person.fullName }) },
                            modifier = Modifier.testTag("link_person_option_${person.id}"),
                        )
                    }
                }

                if (member.role != FamilyRole.OWNER) {
                    HorizontalDivider()
                    Text("الدور الوظيفي:", style = MaterialTheme.typography.labelLarge)
                    val roles = buildList {
                        if (callerRole == FamilyRole.OWNER) add(FamilyRole.ADMIN)
                        add(FamilyRole.BRANCH_MANAGER)
                        add(FamilyRole.MEMBER)
                        add(FamilyRole.RESTRICTED_MEMBER)
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        roles.forEach { candidate ->
                            FilterChip(
                                selected = role == candidate,
                                onClick = { role = candidate },
                                label = { Text(candidate.arabicLabel) },
                                modifier = Modifier.testTag("edit_member_role_${candidate.name}"),
                            )
                        }
                    }

                    Text("حالة العضوية:", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MembershipStatus.entries.forEach { s ->
                            FilterChip(
                                selected = status == s,
                                onClick = { status = s },
                                label = { Text(s.arabicLabel) },
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("السماح بإرسال الدعوات", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = canInvite, onCheckedChange = { canInvite = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("السماح بالتصدير والنسخ", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = canExport, onCheckedChange = { canExport = it })
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("السماح بإدارة الصور", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = canManageMedia, onCheckedChange = { canManageMedia = it })
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        linkedPersonId,
                        branchRootPersonId ?: linkedPersonId,
                        role,
                        canInvite,
                        canExport,
                        canManageMedia,
                        status,
                    )
                },
                modifier = Modifier.minimumInteractiveComponentSize().testTag("save_member_link_button"),
            ) {
                Text("حفظ التعديلات")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("إلغاء")
            }
        },
    )
}

private fun formatDateShort(epochMillis: Long): String {
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(epochMillis))
    }.getOrDefault("-")
}
