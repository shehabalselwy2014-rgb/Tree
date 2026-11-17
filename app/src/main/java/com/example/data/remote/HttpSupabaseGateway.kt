package com.example.data.remote

import com.example.domain.model.FamilyRole
import com.example.domain.model.InvitationStatus
import com.example.domain.model.MembershipStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class HttpSupabaseGateway(
    private val config: SupabaseConfig,
    private val client: OkHttpClient = defaultOkHttpClient(),
) : SupabaseGateway {

    override val isConfigured: Boolean
        get() = config.isConfigured

    override suspend fun signUp(
        email: String,
        password: String,
        displayName: String,
    ): Result<RemoteAuthResult> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfigured()
            val payload = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
                put("data", JSONObject().put("display_name", displayName.trim()))
            }
            val request = baseRequest("${config.url}/auth/v1/signup", accessToken = null)
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()
            val bodyStr = executeJsonRequest(request)
            val obj = JSONObject(bodyStr)
            if (obj.has("access_token") && !obj.isNull("access_token")) {
                RemoteAuthResult.Authenticated(parseSession(obj, fallbackEmail = email.trim(), fallbackName = displayName.trim()))
            } else {
                RemoteAuthResult.ConfirmationRequired(email = email.trim())
            }
        }
    }

    override suspend fun signInWithPassword(
        email: String,
        password: String,
    ): Result<RemoteSession> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfigured()
            val payload = JSONObject().apply {
                put("email", email.trim())
                put("password", password)
            }
            val request = baseRequest("${config.url}/auth/v1/token?grant_type=password", accessToken = null)
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()
            val bodyStr = executeJsonRequest(request)
            parseSession(JSONObject(bodyStr), fallbackEmail = email.trim(), fallbackName = null)
        }
    }

    override suspend fun refreshToken(refreshToken: String): Result<RemoteSession> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfigured()
            val payload = JSONObject().apply {
                put("refresh_token", refreshToken)
            }
            val request = baseRequest("${config.url}/auth/v1/token?grant_type=refresh_token", accessToken = null)
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()
            val bodyStr = executeJsonRequest(request)
            parseSession(JSONObject(bodyStr), fallbackEmail = "", fallbackName = null)
        }
    }

    override suspend fun signOut(accessToken: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            if (!isConfigured || accessToken.isBlank()) return@runCatching
            val request = baseRequest("${config.url}/auth/v1/logout", accessToken = accessToken)
                .post("{}".toRequestBody(JSON_MEDIA))
                .build()
            // Ignore non-fatal server errors on logout (e.g., already expired token)
            runCatching { executeJsonRequest(request) }
            Unit
        }
    }

    override suspend fun upsertUserProfile(
        accessToken: String,
        userId: String,
        email: String,
        displayName: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfigured()
            val now = System.currentTimeMillis()
            val payload = JSONObject().apply {
                put("id", userId)
                put("email", email)
                put("display_name", displayName.trim().ifBlank { email.substringBefore("@") })
                put("updated_at", now)
            }
            val request = baseRequest("${config.url}/rest/v1/user_profiles", accessToken = accessToken)
                .header("Prefer", "resolution=merge-duplicates,return=minimal")
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()
            executeJsonRequest(request)
            Unit
        }
    }

    override suspend fun createFamilySpace(
        accessToken: String,
        familyId: String,
        name: String,
        motto: String,
        rootPersonId: String?,
    ): Result<RemoteFamilyMembership> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfigured()
            val payload = JSONObject().apply {
                put("p_family_id", familyId)
                put("p_name", name.trim())
                put("p_motto", motto.trim())
                put("p_root_person_id", rootPersonId ?: JSONObject.NULL)
            }
            val request = baseRequest("${config.url}/rest/v1/rpc/create_family_space", accessToken = accessToken)
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()
            val obj = JSONObject(executeJsonRequest(request))
            val now = System.currentTimeMillis()
            val createdAt = obj.optLong("created_at", now)
            val updatedAt = obj.optLong("updated_at", createdAt)
            RemoteFamilyMembership(
                familyId = obj.getString("family_id"),
                familyName = obj.getString("name"),
                familyMotto = obj.optString("motto", motto),
                ownerUserId = obj.getString("owner_user_id"),
                rootPersonId = obj.optNullableString("root_person_id"),
                familyVersion = 1L,
                familyCreatedAt = createdAt,
                familyUpdatedAt = updatedAt,
                memberId = obj.getString("member_id"),
                linkedPersonId = obj.optNullableString("root_person_id"),
                role = parseRole(obj.optString("role", FamilyRole.OWNER.name)),
                branchRootPersonId = obj.optNullableString("root_person_id"),
                canInvite = true,
                canExport = true,
                canManageMedia = true,
                status = MembershipStatus.ACTIVE,
                joinedAt = createdAt,
                memberUpdatedAt = updatedAt,
                memberVersion = 1L,
            )
        }
    }

    override suspend fun fetchMyFamilyMemberships(
        accessToken: String,
    ): Result<List<RemoteFamilyMembership>> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfigured()
            val request = baseRequest("${config.url}/rest/v1/rpc/get_my_family_memberships", accessToken = accessToken)
                .post("{}".toRequestBody(JSON_MEDIA))
                .build()
            val raw = executeJsonRequest(request).trim()
            if (raw.isBlank() || raw == "null") return@runCatching emptyList()
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val now = System.currentTimeMillis()
                    add(
                        RemoteFamilyMembership(
                            familyId = obj.getString("family_id"),
                            familyName = obj.getString("family_name"),
                            familyMotto = obj.optString("family_motto", "نوثّق الأصول، ونحفظ امتداد الأجيال"),
                            ownerUserId = obj.getString("owner_user_id"),
                            rootPersonId = obj.optNullableString("root_person_id"),
                            familyVersion = obj.optLong("family_version", 1L),
                            familyCreatedAt = obj.optLong("family_created_at", now),
                            familyUpdatedAt = obj.optLong("family_updated_at", now),
                            memberId = obj.getString("member_id"),
                            linkedPersonId = obj.optNullableString("linked_person_id"),
                            role = parseRole(obj.optString("role", FamilyRole.MEMBER.name)),
                            branchRootPersonId = obj.optNullableString("branch_root_person_id"),
                            canInvite = obj.optBoolean("can_invite", false),
                            canExport = obj.optBoolean("can_export", true),
                            canManageMedia = obj.optBoolean("can_manage_media", false),
                            status = parseStatus(obj.optString("status", MembershipStatus.ACTIVE.name)),
                            joinedAt = obj.optLong("joined_at", now),
                            memberUpdatedAt = obj.optLong("member_updated_at", now),
                            memberVersion = obj.optLong("member_version", 1L),
                        )
                    )
                }
            }
        }
    }

    override suspend fun createFamilyInvitation(
        accessToken: String,
        familyId: String,
        targetPersonId: String?,
        assignedRole: FamilyRole,
        branchRootPersonId: String?,
        inviteCodeHash: String,
        inviteCodeHint: String,
        maxUses: Int,
        expiresAt: Long,
    ): Result<RemoteInvitation> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfigured()
            val payload = JSONObject().apply {
                put("p_family_id", familyId)
                put("p_target_person_id", targetPersonId ?: JSONObject.NULL)
                put("p_assigned_role", assignedRole.name)
                put("p_branch_root_person_id", branchRootPersonId ?: JSONObject.NULL)
                put("p_invite_code_hash", inviteCodeHash)
                put("p_invite_code_hint", inviteCodeHint)
                put("p_max_uses", maxUses)
                put("p_expires_at", expiresAt)
            }
            val request = baseRequest("${config.url}/rest/v1/rpc/create_family_invitation", accessToken = accessToken)
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()
            parseInvitation(JSONObject(executeJsonRequest(request)))
        }
    }

    override suspend fun revokeFamilyInvitation(
        accessToken: String,
        invitationId: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfigured()
            val payload = JSONObject().put("p_invitation_id", invitationId)
            val request = baseRequest("${config.url}/rest/v1/rpc/revoke_family_invitation", accessToken = accessToken)
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()
            executeJsonRequest(request)
            Unit
        }
    }

    override suspend fun fetchFamilyInvitations(
        accessToken: String,
        familyId: String,
    ): Result<List<RemoteInvitation>> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfigured()
            val payload = JSONObject().put("p_family_id", familyId)
            val request = baseRequest("${config.url}/rest/v1/rpc/get_family_invitations", accessToken = accessToken)
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()
            val raw = executeJsonRequest(request).trim()
            if (raw.isBlank() || raw == "null") return@runCatching emptyList()
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    add(parseInvitation(arr.getJSONObject(i)))
                }
            }
        }
    }

    override suspend fun acceptFamilyInvitation(
        accessToken: String,
        inviteCode: String,
    ): Result<RemoteFamilyMembership> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfigured()
            val payload = JSONObject().put("p_invite_code", inviteCode.trim())
            val request = baseRequest("${config.url}/rest/v1/rpc/accept_family_invitation", accessToken = accessToken)
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()
            val obj = JSONObject(executeJsonRequest(request))
            val now = System.currentTimeMillis()
            RemoteFamilyMembership(
                familyId = obj.getString("family_id"),
                familyName = obj.optNullableString("family_name") ?: "عائلة مشتركة",
                familyMotto = obj.optNullableString("family_motto") ?: "نوثّق الأصول، ونحفظ امتداد الأجيال",
                ownerUserId = obj.optNullableString("owner_user_id").orEmpty(),
                rootPersonId = obj.optNullableString("root_person_id"),
                familyVersion = obj.optLong("family_version", 1L),
                familyCreatedAt = obj.optLong("family_created_at", now),
                familyUpdatedAt = obj.optLong("family_updated_at", now),
                memberId = obj.getString("member_id"),
                linkedPersonId = obj.optNullableString("linked_person_id"),
                role = parseRole(obj.optString("role", FamilyRole.MEMBER.name)),
                branchRootPersonId = obj.optNullableString("branch_root_person_id"),
                canInvite = obj.optBoolean("can_invite", false),
                canExport = obj.optBoolean("can_export", true),
                canManageMedia = obj.optBoolean("can_manage_media", false),
                status = parseStatus(obj.optString("status", MembershipStatus.ACTIVE.name)),
                joinedAt = obj.optLong("joined_at", now),
                memberUpdatedAt = obj.optLong("member_updated_at", now),
                memberVersion = obj.optLong("member_version", 1L),
            )
        }
    }

    override suspend fun fetchFamilyMembersWithProfiles(
        accessToken: String,
        familyId: String,
    ): Result<List<RemoteMemberProfile>> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfigured()
            val payload = JSONObject().put("p_family_id", familyId)
            val request = baseRequest("${config.url}/rest/v1/rpc/get_family_members_with_profiles", accessToken = accessToken)
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()
            val raw = executeJsonRequest(request).trim()
            if (raw.isBlank() || raw == "null") return@runCatching emptyList()
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    add(parseMemberProfile(arr.getJSONObject(i)))
                }
            }
        }
    }

    override suspend fun updateFamilyMemberLinkAndRole(
        accessToken: String,
        familyId: String,
        memberId: String,
        linkedPersonId: String?,
        branchRootPersonId: String?,
        role: FamilyRole,
        canInvite: Boolean,
        canExport: Boolean,
        canManageMedia: Boolean,
        status: MembershipStatus,
    ): Result<RemoteMemberProfile> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfigured()
            val payload = JSONObject().apply {
                put("p_family_id", familyId)
                put("p_member_id", memberId)
                put("p_linked_person_id", linkedPersonId ?: JSONObject.NULL)
                put("p_branch_root_person_id", branchRootPersonId ?: JSONObject.NULL)
                put("p_role", role.name)
                put("p_can_invite", canInvite)
                put("p_can_export", canExport)
                put("p_can_manage_media", canManageMedia)
                put("p_status", status.name)
            }
            val request = baseRequest("${config.url}/rest/v1/rpc/update_family_member_link_and_role", accessToken = accessToken)
                .post(payload.toString().toRequestBody(JSON_MEDIA))
                .build()
            parseMemberProfile(JSONObject(executeJsonRequest(request)))
        }
    }

    override suspend fun pushGenealogyMutation(
        accessToken: String,
        familyId: String,
        entityType: String,
        entityId: String,
        operation: String,
        clientVersion: Long,
        forceOverwrite: Boolean,
        payloadJson: String,
    ): Result<RemotePushOutcome> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfigured()
            val parsedPayload = runCatching { JSONObject(payloadJson) }.getOrElse { JSONObject() }
            val body = JSONObject().apply {
                put("p_family_id", familyId)
                put("p_entity_type", entityType)
                put("p_entity_id", entityId)
                put("p_operation", operation)
                put("p_client_version", clientVersion)
                put("p_force_overwrite", forceOverwrite)
                put("p_payload", parsedPayload)
            }
            val request = baseRequest("${config.url}/rest/v1/rpc/sync_push_genealogy_mutation", accessToken = accessToken)
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .build()
            val obj = JSONObject(executeJsonRequest(request))
            when (obj.optString("status", "APPLIED").uppercase()) {
                "CONFLICT" -> RemotePushOutcome.Conflict(
                    serverVersion = obj.optLong("server_version", clientVersion + 1L),
                    serverRecordJson = obj.optJSONObject("server_record")?.toString() ?: "{}",
                )
                else -> RemotePushOutcome.Applied(
                    version = obj.optLong("version", clientVersion),
                    updatedAtMillis = obj.optLong("updated_at", System.currentTimeMillis()),
                )
            }
        }
    }

    override suspend fun pullFamilyDelta(
        accessToken: String,
        familyId: String,
        sinceUpdatedAt: Long,
    ): Result<RemoteSyncDelta> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfigured()
            val body = JSONObject().apply {
                put("p_family_id", familyId)
                put("p_since_updated_at", sinceUpdatedAt)
            }
            val request = baseRequest("${config.url}/rest/v1/rpc/sync_pull_family_delta", accessToken = accessToken)
                .post(body.toString().toRequestBody(JSON_MEDIA))
                .build()
            val raw = executeJsonRequest(request).ifBlank { "{}" }
            val obj = JSONObject(raw)
            RemoteSyncDelta(
                serverTimeMillis = obj.optLong("server_time", System.currentTimeMillis()),
                rawJson = raw,
            )
        }
    }

    override suspend fun uploadFamilyMedia(
        accessToken: String,
        objectKey: String,
        bytes: ByteArray,
        mimeType: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfigured()
            val encodedPath = encodeStoragePath(objectKey)
            val request = Request.Builder()
                .url("${config.url}/storage/v1/object/$STORAGE_BUCKET/$encodedPath")
                .header("apikey", config.anonKey)
                .header("Authorization", "Bearer $accessToken")
                .header("x-upsert", "true")
                .post(bytes.toRequestBody(mimeType.toMediaType()))
                .build()
            executeJsonRequest(request)
            objectKey
        }
    }

    override suspend fun downloadFamilyMedia(
        accessToken: String,
        objectKey: String,
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfigured()
            val encodedPath = encodeStoragePath(objectKey)
            val request = Request.Builder()
                .url("${config.url}/storage/v1/object/authenticated/$STORAGE_BUCKET/$encodedPath")
                .header("apikey", config.anonKey)
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            val response = try {
                client.newCall(request).execute()
            } catch (io: IOException) {
                throw IOException("تعذر تنزيل الوسائط من الخادم السحابي.", io)
            }
            response.use { res ->
                if (!res.isSuccessful) {
                    val errBody = res.body?.string().orEmpty()
                    throw IllegalStateException(translateSupabaseError(res.code, errBody))
                }
                res.body?.bytes() ?: ByteArray(0)
            }
        }
    }

    private fun requireConfigured() {
        if (!isConfigured) {
            throw IllegalStateException(
                "الخادم السحابي غير مهيأ بعد. أضف SUPABASE_URL و SUPABASE_ANON_KEY في لوحة الأسرار (Secrets) بـ AI Studio."
            )
        }
    }

    private fun baseRequest(url: String, accessToken: String?): Request.Builder {
        val bearer = accessToken?.takeIf { it.isNotBlank() } ?: config.anonKey
        return Request.Builder()
            .url(url)
            .header("apikey", config.anonKey)
            .header("Authorization", "Bearer $bearer")
            .header("Accept", "application/json")
    }

    private fun executeJsonRequest(request: Request): String {
        val response = try {
            client.newCall(request).execute()
        } catch (io: IOException) {
            throw IOException("تعذر الاتصال بالخادم السحابي. تحقق من اتصال الإنترنت وحاول مجدداً.", io)
        }
        response.use { res ->
            val body = res.body?.string().orEmpty()
            if (!res.isSuccessful) {
                throw IllegalStateException(translateSupabaseError(res.code, body))
            }
            return body
        }
    }

    private fun parseSession(
        obj: JSONObject,
        fallbackEmail: String,
        fallbackName: String?,
    ): RemoteSession {
        val userObj = obj.optJSONObject("user")
            ?: throw IllegalStateException("استجابة المصادقة غير مكتملة من الخادم.")
        val meta = userObj.optJSONObject("user_metadata")
        val email = userObj.optNullableString("email") ?: fallbackEmail
        val metaName = meta?.optNullableString("display_name")
            ?: meta?.optNullableString("full_name")
        val resolvedName = (metaName ?: fallbackName ?: email.substringBefore("@"))
            .ifBlank { "مستخدم جذور" }
        val expiresInSec = obj.optLong("expires_in", 3600L)
        return RemoteSession(
            userId = userObj.getString("id"),
            email = email,
            displayName = resolvedName,
            accessToken = obj.getString("access_token"),
            refreshToken = obj.optString("refresh_token", ""),
            expiresAtMillis = System.currentTimeMillis() + (expiresInSec * 1000L),
        )
    }

    private fun translateSupabaseError(code: Int, rawBody: String): String {
        val rawMessage = runCatching {
            val err = JSONObject(rawBody)
            err.optNullableString("error_description")
                ?: err.optNullableString("msg")
                ?: err.optNullableString("message")
                ?: err.optNullableString("error")
        }.getOrNull().orEmpty()

        val lower = rawMessage.lowercase()
        return when {
            lower.contains("invalid login credentials") ->
                "البريد الإلكتروني أو كلمة المرور غير صحيحة."
            lower.contains("email not confirmed") ->
                "يرجى تأكيد البريد الإلكتروني أولاً من الرابط المرسل إليك."
            lower.contains("user already registered") || lower.contains("already been registered") ->
                "يوجد حساب مسجل مسبقاً بهذا البريد الإلكتروني."
            lower.contains("password should be at least") || lower.contains("weak_password") ->
                "كلمة المرور ضعيفة؛ يرجى اختيار 6 أحرف أو أرقام على الأقل."
            lower.contains("invalid invitation code") ->
                "رمز الدعوة غير صحيح أو غير موجود."
            lower.contains("invitation has expired") ->
                "انتهت صلاحية رمز الدعوة."
            lower.contains("invitation usage limit reached") ->
                "تم استهلاك الحد الأقصى لاستخدام رمز الدعوة."
            lower.contains("invitation is no longer active") ->
                "هذه الدعوة لم تعد صالحة للاستخدام."
            lower.contains("already linked to another active account") || lower.contains("already linked to another active") ->
                "هذا الفرد في الشجرة مرتبط مسبقاً بحساب مستخدم نشط آخر."
            lower.contains("not authorized to push edits") || lower.contains("outside your branch") ->
                "دورك الحالي لا يملك صلاحية تعديل هذا السجل خارج فرعك العائلي."
            lower.contains("cycle detected") ->
                "تعذر المزامنة بسبب حلقة نسب دائرية غير صالحة."
            lower.contains("exceed 4 concurrent active marriages") ->
                "لا يمكن تجاوز أربع زوجات في الوقت نفسه."
            lower.contains("not authorized") || lower.contains("only family owner") || lower.contains("cannot modify") ->
                "ليست لديك الصلاحية الكافية لتنفيذ هذا الإجراء."
            lower.contains("rate limit") || code == 429 ->
                "تم تجاوز عدد المحاولات المسموح به مؤقتاً. انتظر قليلاً ثم أعد المحاولة."
            rawMessage.isNotBlank() ->
                "تعذر إتمام الطلب ($code): $rawMessage"
            else ->
                "تعذر إتمام الطلب السحابي (رمز الحالة: $code)."
        }
    }

    private fun parseInvitation(obj: JSONObject): RemoteInvitation {
        val now = System.currentTimeMillis()
        return RemoteInvitation(
            id = obj.getString("id"),
            familyId = obj.getString("family_id"),
            targetPersonId = obj.optNullableString("target_person_id"),
            assignedRole = parseRole(obj.optString("assigned_role", FamilyRole.BRANCH_MANAGER.name)),
            branchRootPersonId = obj.optNullableString("branch_root_person_id"),
            inviteCodeHash = obj.optString("invite_code_hash", ""),
            inviteCodeHint = obj.optString("invite_code_hint", "JTH-••••"),
            maxUses = obj.optInt("max_uses", 1),
            usesCount = obj.optInt("uses_count", 0),
            expiresAt = obj.optLong("expires_at", now),
            status = parseInvitationStatus(obj.optString("status", InvitationStatus.PENDING.name)),
            createdByUserId = obj.optString("created_by_user_id", ""),
            acceptedByUserId = obj.optNullableString("accepted_by_user_id"),
            createdAt = obj.optLong("created_at", now),
            updatedAt = obj.optLong("updated_at", now),
        )
    }

    private fun parseMemberProfile(obj: JSONObject): RemoteMemberProfile {
        val now = System.currentTimeMillis()
        val email = obj.optString("email", "")
        val displayName = obj.optNullableString("display_name") ?: email.substringBefore("@").ifBlank { "عضو العائلة" }
        return RemoteMemberProfile(
            memberId = obj.getString("member_id"),
            familyId = obj.getString("family_id"),
            userId = obj.getString("user_id"),
            email = email,
            displayName = displayName,
            linkedPersonId = obj.optNullableString("linked_person_id"),
            role = parseRole(obj.optString("role", FamilyRole.MEMBER.name)),
            branchRootPersonId = obj.optNullableString("branch_root_person_id"),
            canInvite = obj.optBoolean("can_invite", false),
            canExport = obj.optBoolean("can_export", true),
            canManageMedia = obj.optBoolean("can_manage_media", false),
            status = parseStatus(obj.optString("status", MembershipStatus.ACTIVE.name)),
            invitedByUserId = obj.optNullableString("invited_by_user_id"),
            joinedAt = obj.optLong("joined_at", now),
            updatedAt = obj.optLong("updated_at", now),
            version = obj.optLong("version", 1L),
        )
    }

    private fun parseRole(raw: String): FamilyRole =
        runCatching { FamilyRole.valueOf(raw) }.getOrDefault(FamilyRole.MEMBER)

    private fun parseStatus(raw: String): MembershipStatus =
        runCatching { MembershipStatus.valueOf(raw) }.getOrDefault(MembershipStatus.ACTIVE)

    private fun parseInvitationStatus(raw: String): InvitationStatus =
        runCatching { InvitationStatus.valueOf(raw) }.getOrDefault(InvitationStatus.PENDING)

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun encodeStoragePath(objectKey: String): String =
        objectKey.split("/").joinToString("/") { segment ->
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        private const val STORAGE_BUCKET = "family-media"

        fun defaultOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }
}
