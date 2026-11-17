package com.example.domain.model

data class AuthSession(
    val userId: String,
    val email: String,
    val displayName: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val activeFamilyId: String? = null,
) {
    fun isAccessTokenExpired(nowMillis: Long = System.currentTimeMillis()): Boolean =
        accessToken.isBlank() || expiresAtMillis <= (nowMillis + 30_000L)
}

sealed interface AuthActionResult {
    data class Authenticated(val session: AuthSession) : AuthActionResult
    data class EmailConfirmationRequired(
        val email: String,
        val messageArabic: String,
    ) : AuthActionResult
}

data class CloudFamilySummary(
    val family: FamilySpace,
    val membership: FamilyMember,
)

data class CreatedInvitationResult(
    val invitation: FamilyInvitation,
    val plainInviteCode: String,
)

data class FamilyMemberWithProfile(
    val member: FamilyMember,
    val displayName: String,
    val email: String,
)

enum class ConflictResolutionChoice(val arabicLabel: String) {
    KEEP_LOCAL("الاحتفاظ بنسختي"),
    USE_SERVER("اعتماد نسخة الخادم"),
}

data class SyncCycleReport(
    val pushedCount: Int,
    val pulledCount: Int,
    val conflictCount: Int,
    val uploadedMediaCount: Int = 0,
    val downloadedMediaCount: Int = 0,
    val syncedAtMillis: Long = System.currentTimeMillis(),
)

