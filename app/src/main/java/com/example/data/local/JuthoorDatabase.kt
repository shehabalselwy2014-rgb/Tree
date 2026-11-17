package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.local.converter.GenealogyConverters
import com.example.data.local.dao.AuditLogDao
import com.example.data.local.dao.EducationDao
import com.example.data.local.dao.EventDao
import com.example.data.local.dao.FamilyGroupDao
import com.example.data.local.dao.FamilyInvitationDao
import com.example.data.local.dao.FamilyMemberDao
import com.example.data.local.dao.FamilySpaceDao
import com.example.data.local.dao.MarriageDao
import com.example.data.local.dao.ParentChildDao
import com.example.data.local.dao.PersonDao
import com.example.data.local.dao.PhotoDao
import com.example.data.local.dao.SyncConflictDao
import com.example.data.local.dao.SyncMetadataDao
import com.example.data.local.dao.SyncOutboxDao
import com.example.data.local.dao.UserAccountDao
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.EducationEntity
import com.example.data.local.entity.EventEntity
import com.example.data.local.entity.FamilyGroupEntity
import com.example.data.local.entity.FamilyInvitationEntity
import com.example.data.local.entity.FamilyMemberEntity
import com.example.data.local.entity.FamilySpaceEntity
import com.example.data.local.entity.MarriageEntity
import com.example.data.local.entity.ParentChildEntity
import com.example.data.local.entity.PersonEntity
import com.example.data.local.entity.PhotoEntity
import com.example.data.local.entity.PhotoPersonCrossRef
import com.example.data.local.entity.SyncConflictEntity
import com.example.data.local.entity.SyncMetadataEntity
import com.example.data.local.entity.SyncOutboxEntity
import com.example.data.local.entity.UserAccountEntity

@Database(
    entities = [
        PersonEntity::class,
        MarriageEntity::class,
        ParentChildEntity::class,
        EducationEntity::class,
        PhotoEntity::class,
        PhotoPersonCrossRef::class,
        EventEntity::class,
        FamilyGroupEntity::class,
        AuditLogEntity::class,
        FamilySpaceEntity::class,
        UserAccountEntity::class,
        FamilyMemberEntity::class,
        FamilyInvitationEntity::class,
        SyncOutboxEntity::class,
        SyncMetadataEntity::class,
        SyncConflictEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(GenealogyConverters::class)
abstract class JuthoorDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun marriageDao(): MarriageDao
    abstract fun parentChildDao(): ParentChildDao
    abstract fun educationDao(): EducationDao
    abstract fun photoDao(): PhotoDao
    abstract fun eventDao(): EventDao
    abstract fun familyGroupDao(): FamilyGroupDao
    abstract fun auditLogDao(): AuditLogDao

    // Multi-user & Sync DAOs (Phase 9.2)
    abstract fun familySpaceDao(): FamilySpaceDao
    abstract fun userAccountDao(): UserAccountDao
    abstract fun familyMemberDao(): FamilyMemberDao
    abstract fun familyInvitationDao(): FamilyInvitationDao
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun syncConflictDao(): SyncConflictDao

    companion object {
        const val DATABASE_NAME = "juthoor_genealogy.db"

        @Volatile
        private var instance: JuthoorDatabase? = null

        fun getInstance(context: Context): JuthoorDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    JuthoorDatabase::class.java,
                    DATABASE_NAME,
                )
                    .addMigrations(JuthoorDatabaseMigrations.MIGRATION_1_2)
                    // Non-destructive migrations policy: never call fallbackToDestructiveMigration().
                    .build()
                    .also { instance = it }
            }
        }
    }
}
