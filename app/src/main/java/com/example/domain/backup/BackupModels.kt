package com.example.domain.backup

data class BackupCounts(
    val personsCount: Int = 0,
    val marriagesCount: Int = 0,
    val parentChildCount: Int = 0,
    val educationCount: Int = 0,
    val photosCount: Int = 0,
    val eventsCount: Int = 0,
    val familyGroupsCount: Int = 0,
    val auditLogsCount: Int = 0,
    val mediaFilesCount: Int = 0,
)

data class BackupManifest(
    val formatVersion: Int,
    val schemaVersion: Int,
    val appVersion: String,
    val exportedAtMillis: Long,
    val familyTitle: String,
    val familyMotto: String,
    val counts: BackupCounts,
    val databaseSha256: String,
    val mediaSha256: Map<String, String>,
    val overallPackageSha256: String,
)

data class BackupPreview(
    val manifest: BackupManifest,
    val rawDatabaseJson: String,
    val mediaEntries: Map<String, ByteArray>,
)

data class RestoreResult(
    val restoredCounts: BackupCounts,
    val familyTitle: String,
)
