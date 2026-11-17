package com.example.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.domain.model.MultiUserDefaults

object JuthoorDatabaseMigrations {

    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val defaultFamilyId = MultiUserDefaults.DEFAULT_LOCAL_FAMILY_ID

            // 1. Upgrade existing 9 tables non-destructively (preserving every row & FK)
            db.execSQL("ALTER TABLE `persons` ADD COLUMN `familyId` TEXT NOT NULL DEFAULT '$defaultFamilyId'")
            db.execSQL("ALTER TABLE `persons` ADD COLUMN `remoteAvatarKey` TEXT")
            db.execSQL("ALTER TABLE `persons` ADD COLUMN `avatarSha256` TEXT")
            db.execSQL("ALTER TABLE `persons` ADD COLUMN `createdByUserId` TEXT")
            db.execSQL("ALTER TABLE `persons` ADD COLUMN `updatedByUserId` TEXT")
            db.execSQL("ALTER TABLE `persons` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `persons` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `persons` ADD COLUMN `deletedAt` INTEGER")
            db.execSQL("ALTER TABLE `persons` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_persons_familyId` ON `persons` (`familyId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_persons_isDeleted` ON `persons` (`isDeleted`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_persons_syncState` ON `persons` (`syncState`)")

            db.execSQL("ALTER TABLE `marriages` ADD COLUMN `familyId` TEXT NOT NULL DEFAULT '$defaultFamilyId'")
            db.execSQL("ALTER TABLE `marriages` ADD COLUMN `createdByUserId` TEXT")
            db.execSQL("ALTER TABLE `marriages` ADD COLUMN `updatedByUserId` TEXT")
            db.execSQL("ALTER TABLE `marriages` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `marriages` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `marriages` ADD COLUMN `deletedAt` INTEGER")
            db.execSQL("ALTER TABLE `marriages` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_marriages_familyId` ON `marriages` (`familyId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_marriages_isDeleted` ON `marriages` (`isDeleted`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_marriages_syncState` ON `marriages` (`syncState`)")

            db.execSQL("ALTER TABLE `parent_child` ADD COLUMN `familyId` TEXT NOT NULL DEFAULT '$defaultFamilyId'")
            db.execSQL("ALTER TABLE `parent_child` ADD COLUMN `createdByUserId` TEXT")
            db.execSQL("ALTER TABLE `parent_child` ADD COLUMN `updatedByUserId` TEXT")
            db.execSQL("ALTER TABLE `parent_child` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `parent_child` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `parent_child` ADD COLUMN `deletedAt` INTEGER")
            db.execSQL("ALTER TABLE `parent_child` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `parent_child` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
            db.execSQL("UPDATE `parent_child` SET `updatedAt` = `createdAt`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_parent_child_familyId` ON `parent_child` (`familyId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_parent_child_isDeleted` ON `parent_child` (`isDeleted`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_parent_child_syncState` ON `parent_child` (`syncState`)")

            db.execSQL("ALTER TABLE `education` ADD COLUMN `familyId` TEXT NOT NULL DEFAULT '$defaultFamilyId'")
            db.execSQL("ALTER TABLE `education` ADD COLUMN `createdByUserId` TEXT")
            db.execSQL("ALTER TABLE `education` ADD COLUMN `updatedByUserId` TEXT")
            db.execSQL("ALTER TABLE `education` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `education` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `education` ADD COLUMN `deletedAt` INTEGER")
            db.execSQL("ALTER TABLE `education` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `education` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `education` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_education_familyId` ON `education` (`familyId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_education_isDeleted` ON `education` (`isDeleted`)")

            db.execSQL("ALTER TABLE `photos` ADD COLUMN `familyId` TEXT NOT NULL DEFAULT '$defaultFamilyId'")
            db.execSQL("ALTER TABLE `photos` ADD COLUMN `remoteStorageKey` TEXT")
            db.execSQL("ALTER TABLE `photos` ADD COLUMN `sha256` TEXT")
            db.execSQL("ALTER TABLE `photos` ADD COLUMN `createdByUserId` TEXT")
            db.execSQL("ALTER TABLE `photos` ADD COLUMN `updatedByUserId` TEXT")
            db.execSQL("ALTER TABLE `photos` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `photos` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `photos` ADD COLUMN `deletedAt` INTEGER")
            db.execSQL("ALTER TABLE `photos` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `photos` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
            db.execSQL("UPDATE `photos` SET `updatedAt` = `createdAt`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_familyId` ON `photos` (`familyId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_isDeleted` ON `photos` (`isDeleted`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_photos_syncState` ON `photos` (`syncState`)")

            db.execSQL("ALTER TABLE `photo_person_cross_ref` ADD COLUMN `familyId` TEXT NOT NULL DEFAULT '$defaultFamilyId'")
            db.execSQL("ALTER TABLE `photo_person_cross_ref` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `photo_person_cross_ref` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `photo_person_cross_ref` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_photo_person_cross_ref_familyId` ON `photo_person_cross_ref` (`familyId`)")

            db.execSQL("ALTER TABLE `events` ADD COLUMN `familyId` TEXT NOT NULL DEFAULT '$defaultFamilyId'")
            db.execSQL("ALTER TABLE `events` ADD COLUMN `createdByUserId` TEXT")
            db.execSQL("ALTER TABLE `events` ADD COLUMN `updatedByUserId` TEXT")
            db.execSQL("ALTER TABLE `events` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `events` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `events` ADD COLUMN `deletedAt` INTEGER")
            db.execSQL("ALTER TABLE `events` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `events` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `events` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_familyId` ON `events` (`familyId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_events_isDeleted` ON `events` (`isDeleted`)")

            db.execSQL("ALTER TABLE `family_groups` ADD COLUMN `familyId` TEXT NOT NULL DEFAULT '$defaultFamilyId'")
            db.execSQL("ALTER TABLE `family_groups` ADD COLUMN `createdByUserId` TEXT")
            db.execSQL("ALTER TABLE `family_groups` ADD COLUMN `updatedByUserId` TEXT")
            db.execSQL("ALTER TABLE `family_groups` ADD COLUMN `version` INTEGER NOT NULL DEFAULT 1")
            db.execSQL("ALTER TABLE `family_groups` ADD COLUMN `isDeleted` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `family_groups` ADD COLUMN `deletedAt` INTEGER")
            db.execSQL("ALTER TABLE `family_groups` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `family_groups` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
            db.execSQL("UPDATE `family_groups` SET `updatedAt` = `createdAt`")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_family_groups_familyId` ON `family_groups` (`familyId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_family_groups_isDeleted` ON `family_groups` (`isDeleted`)")

            db.execSQL("ALTER TABLE `audit_log` ADD COLUMN `familyId` TEXT NOT NULL DEFAULT '$defaultFamilyId'")
            db.execSQL("ALTER TABLE `audit_log` ADD COLUMN `actorUserId` TEXT")
            db.execSQL("ALTER TABLE `audit_log` ADD COLUMN `actorMemberRole` TEXT")
            db.execSQL("ALTER TABLE `audit_log` ADD COLUMN `syncState` TEXT NOT NULL DEFAULT 'LOCAL_ONLY'")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_log_familyId` ON `audit_log` (`familyId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_audit_log_actorUserId` ON `audit_log` (`actorUserId`)")

            // 2. Create new multi-user & sync tables
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `families` (
                    `id` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `motto` TEXT NOT NULL,
                    `ownerUserId` TEXT,
                    `rootPersonId` TEXT,
                    `version` INTEGER NOT NULL,
                    `isDeleted` INTEGER NOT NULL,
                    `deletedAt` INTEGER,
                    `syncState` TEXT NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_families_ownerUserId` ON `families` (`ownerUserId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_families_isDeleted` ON `families` (`isDeleted`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_families_syncState` ON `families` (`syncState`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `user_accounts` (
                    `id` TEXT NOT NULL,
                    `email` TEXT NOT NULL,
                    `displayName` TEXT NOT NULL,
                    `avatarStorageKey` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_user_accounts_email` ON `user_accounts` (`email`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `family_members` (
                    `id` TEXT NOT NULL,
                    `familyId` TEXT NOT NULL,
                    `userId` TEXT NOT NULL,
                    `linkedPersonId` TEXT,
                    `role` TEXT NOT NULL,
                    `branchRootPersonId` TEXT,
                    `canInvite` INTEGER NOT NULL,
                    `canExport` INTEGER NOT NULL,
                    `canManageMedia` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `invitedByUserId` TEXT,
                    `joinedAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    `version` INTEGER NOT NULL,
                    `syncState` TEXT NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`familyId`) REFERENCES `families`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`userId`) REFERENCES `user_accounts`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`linkedPersonId`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_family_members_familyId` ON `family_members` (`familyId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_family_members_userId` ON `family_members` (`userId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_family_members_linkedPersonId` ON `family_members` (`linkedPersonId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_family_members_familyId_userId` ON `family_members` (`familyId`, `userId`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_family_members_familyId_linkedPersonId` ON `family_members` (`familyId`, `linkedPersonId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `family_invitations` (
                    `id` TEXT NOT NULL,
                    `familyId` TEXT NOT NULL,
                    `targetPersonId` TEXT,
                    `assignedRole` TEXT NOT NULL,
                    `branchRootPersonId` TEXT,
                    `inviteCodeHash` TEXT NOT NULL,
                    `inviteCodeHint` TEXT NOT NULL,
                    `maxUses` INTEGER NOT NULL,
                    `usesCount` INTEGER NOT NULL,
                    `expiresAt` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `createdByUserId` TEXT NOT NULL,
                    `acceptedByUserId` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`),
                    FOREIGN KEY(`familyId`) REFERENCES `families`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`targetPersonId`) REFERENCES `persons`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_family_invitations_familyId` ON `family_invitations` (`familyId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_family_invitations_targetPersonId` ON `family_invitations` (`targetPersonId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_family_invitations_status` ON `family_invitations` (`status`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_family_invitations_inviteCodeHash` ON `family_invitations` (`inviteCodeHash`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `sync_outbox` (
                    `id` TEXT NOT NULL,
                    `familyId` TEXT NOT NULL,
                    `entityType` TEXT NOT NULL,
                    `entityId` TEXT NOT NULL,
                    `operation` TEXT NOT NULL,
                    `payloadJson` TEXT NOT NULL,
                    `baseVersion` INTEGER NOT NULL,
                    `status` TEXT NOT NULL,
                    `retryCount` INTEGER NOT NULL,
                    `lastError` TEXT,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_familyId` ON `sync_outbox` (`familyId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_status` ON `sync_outbox` (`status`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_createdAt` ON `sync_outbox` (`createdAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_outbox_entityId` ON `sync_outbox` (`entityId`)")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `sync_metadata` (
                    `familyId` TEXT NOT NULL,
                    `entityType` TEXT NOT NULL,
                    `lastPulledUpdatedAt` INTEGER NOT NULL,
                    `lastSyncedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`familyId`, `entityType`)
                )
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `sync_conflicts` (
                    `id` TEXT NOT NULL,
                    `familyId` TEXT NOT NULL,
                    `entityType` TEXT NOT NULL,
                    `entityId` TEXT NOT NULL,
                    `localVersion` INTEGER NOT NULL,
                    `serverVersion` INTEGER NOT NULL,
                    `localPayloadJson` TEXT NOT NULL,
                    `serverPayloadJson` TEXT NOT NULL,
                    `conflictReason` TEXT NOT NULL,
                    `detectedAt` INTEGER NOT NULL,
                    `resolved` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_conflicts_familyId` ON `sync_conflicts` (`familyId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_conflicts_resolved` ON `sync_conflicts` (`resolved`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_sync_conflicts_entityId` ON `sync_conflicts` (`entityId`)")

            // 3. Preserve pre-existing local family workspace container ONLY if v1 database already held real user records
            val cursor = db.query("SELECT (SELECT COUNT(*) FROM `persons`) + (SELECT COUNT(*) FROM `family_groups`)")
            val hasExistingRecords = cursor.use { if (it.moveToFirst()) it.getInt(0) > 0 else false }
            if (hasExistingRecords) {
                val now = System.currentTimeMillis()
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `families`
                    (`id`, `name`, `motto`, `ownerUserId`, `rootPersonId`, `version`, `isDeleted`, `deletedAt`, `syncState`, `createdAt`, `updatedAt`)
                    VALUES (
                        '$defaultFamilyId',
                        'عائلة جِذور',
                        'نوثّق الأصول، ونحفظ امتداد الأجيال',
                        NULL,
                        (SELECT `rootPersonId` FROM `family_groups` ORDER BY `createdAt` ASC LIMIT 1),
                        1,
                        0,
                        NULL,
                        'LOCAL_ONLY',
                        $now,
                        $now
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
