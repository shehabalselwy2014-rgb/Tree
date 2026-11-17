package com.example.data.local.converter

import androidx.room.TypeConverter
import com.example.domain.model.AuditOperation
import com.example.domain.model.DatePrecision
import com.example.domain.model.FamilyEventType
import com.example.domain.model.FamilyRole
import com.example.domain.model.Gender
import com.example.domain.model.InvitationStatus
import com.example.domain.model.MaritalStatus
import com.example.domain.model.MarriageRecordStatus
import com.example.domain.model.MediaType
import com.example.domain.model.MembershipStatus
import com.example.domain.model.ParentRelationshipType
import com.example.domain.model.SyncEntityType
import com.example.domain.model.SyncOutboxOperation
import com.example.domain.model.SyncOutboxStatus
import com.example.domain.model.SyncState

class GenealogyConverters {
    @TypeConverter fun fromGender(value: Gender): String = value.name
    @TypeConverter fun toGender(value: String): Gender = enumValueOf(value)

    @TypeConverter fun fromDatePrecision(value: DatePrecision): String = value.name
    @TypeConverter fun toDatePrecision(value: String): DatePrecision = enumValueOf(value)

    @TypeConverter fun fromMaritalStatus(value: MaritalStatus): String = value.name
    @TypeConverter fun toMaritalStatus(value: String): MaritalStatus = enumValueOf(value)

    @TypeConverter fun fromMarriageStatus(value: MarriageRecordStatus): String = value.name
    @TypeConverter fun toMarriageStatus(value: String): MarriageRecordStatus = enumValueOf(value)

    @TypeConverter fun fromParentRelationshipType(value: ParentRelationshipType): String = value.name
    @TypeConverter fun toParentRelationshipType(value: String): ParentRelationshipType = enumValueOf(value)

    @TypeConverter fun fromMediaType(value: MediaType): String = value.name
    @TypeConverter fun toMediaType(value: String): MediaType = enumValueOf(value)

    @TypeConverter fun fromEventType(value: FamilyEventType): String = value.name
    @TypeConverter fun toEventType(value: String): FamilyEventType = enumValueOf(value)

    @TypeConverter fun fromAuditOperation(value: AuditOperation): String = value.name
    @TypeConverter fun toAuditOperation(value: String): AuditOperation = enumValueOf(value)

    @TypeConverter fun fromFamilyRole(value: FamilyRole): String = value.name
    @TypeConverter fun toFamilyRole(value: String): FamilyRole = enumValueOf(value)

    @TypeConverter fun fromMembershipStatus(value: MembershipStatus): String = value.name
    @TypeConverter fun toMembershipStatus(value: String): MembershipStatus = enumValueOf(value)

    @TypeConverter fun fromInvitationStatus(value: InvitationStatus): String = value.name
    @TypeConverter fun toInvitationStatus(value: String): InvitationStatus = enumValueOf(value)

    @TypeConverter fun fromSyncState(value: SyncState): String = value.name
    @TypeConverter fun toSyncState(value: String): SyncState = enumValueOf(value)

    @TypeConverter fun fromSyncEntityType(value: SyncEntityType): String = value.name
    @TypeConverter fun toSyncEntityType(value: String): SyncEntityType = enumValueOf(value)

    @TypeConverter fun fromSyncOutboxOperation(value: SyncOutboxOperation): String = value.name
    @TypeConverter fun toSyncOutboxOperation(value: String): SyncOutboxOperation = enumValueOf(value)

    @TypeConverter fun fromSyncOutboxStatus(value: SyncOutboxStatus): String = value.name
    @TypeConverter fun toSyncOutboxStatus(value: String): SyncOutboxStatus = enumValueOf(value)
}
