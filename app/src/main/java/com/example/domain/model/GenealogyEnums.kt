package com.example.domain.model

enum class Gender(val arabicLabel: String) {
    MALE("ذكر"),
    FEMALE("أنثى"),
}

enum class DatePrecision(val arabicLabel: String) {
    UNKNOWN("غير معروف"),
    EXACT("تاريخ دقيق"),
    MONTH_YEAR("شهر وسنة"),
    YEAR_ONLY("سنة فقط"),
    APPROXIMATE("تاريخ تقريبي"),
}

enum class MaritalStatus(val arabicLabel: String) {
    SINGLE("أعزب / عزباء"),
    MARRIED("متزوج / متزوجة"),
    DIVORCED("مطلق / مطلقة"),
    WIDOWED("أرمل / أرملة"),
    UNKNOWN("غير محدد"),
}

enum class MarriageRecordStatus(val arabicLabel: String) {
    ACTIVE("زواج قائم"),
    DIVORCED("انتهى بالطلاق"),
    ENDED_BY_DEATH("انتهى بالوفاة"),
}

enum class ParentRelationshipType(
    val arabicLabel: String,
    val isBiological: Boolean,
    val isConfirmed: Boolean,
) {
    BIOLOGICAL_FATHER("أب بيولوجي", isBiological = true, isConfirmed = true),
    BIOLOGICAL_MOTHER("أم بيولوجية", isBiological = true, isConfirmed = true),
    ADOPTIVE_GUARDIAN("كفالة / رعاية (غير منسوب)", isBiological = false, isConfirmed = true),
    UNCONFIRMED_FATHER("أبوة غير مؤكدة", isBiological = true, isConfirmed = false),
    UNCONFIRMED_MOTHER("أمومة غير مؤكدة", isBiological = true, isConfirmed = false),
}

enum class MediaType(val arabicLabel: String) {
    PERSON_PORTRAIT("صورة شخصية"),
    GROUP_PHOTO("صورة جماعية"),
    ANCESTOR_PHOTO("صورة أجداد"),
    HISTORICAL_PHOTO("صورة تاريخية"),
    FAMILY_DOCUMENT("وثيقة عائلية"),
}

enum class FamilyEventType(val arabicLabel: String) {
    BIRTH("ولادة"),
    MARRIAGE("زواج"),
    DEATH("وفاة"),
    GRADUATION("تخرج"),
    MEMORIAL("ذكرى"),
    FAMILY_GATHERING("مناسبة عائلية"),
    OTHER("حدث آخر"),
}

enum class AuditOperation {
    CREATE,
    UPDATE,
    DELETE,
    RESTORE,
}

enum class FamilyRole(val arabicLabel: String, val privilegeRank: Int) {
    OWNER("المؤسس والمالك", 5),
    ADMIN("مدير مساعد", 4),
    BRANCH_MANAGER("مدير فرع / أسرة", 3),
    MEMBER("عضو مشارك بالمشاهدة", 2),
    RESTRICTED_MEMBER("عضو بمشاهدة محدودة", 1),
}

enum class MembershipStatus(val arabicLabel: String) {
    ACTIVE("نشط"),
    SUSPENDED("معلّق"),
    REVOKED("ملغى"),
}

enum class InvitationStatus(val arabicLabel: String) {
    PENDING("بانتظار القبول"),
    ACCEPTED("مقبولة"),
    REJECTED("مرفوضة"),
    REVOKED("ملغاة"),
    EXPIRED("منتهية الصلاحية"),
}

enum class SyncState(val arabicLabel: String) {
    LOCAL_ONLY("محلي فقط"),
    PENDING_UPSERT("بانتظار الرفع"),
    PENDING_DELETE("بانتظار الحذف"),
    SYNCED("متزامن"),
    CONFLICT("تعارض تعديل"),
}

enum class SyncEntityType(val arabicLabel: String) {
    FAMILY("مساحة العائلة"),
    FAMILY_GROUP("الفرع العائلي"),
    PERSON("الفرد"),
    MARRIAGE("سجل الزواج"),
    PARENT_CHILD("علاقة النسب"),
    EDUCATION("المؤهل الدراسي"),
    PHOTO("الصورة"),
    PHOTO_TAG("وسم الصورة"),
    EVENT("الحدث العائلي"),
    FAMILY_MEMBER("عضو العائلة"),
}

enum class SyncOutboxOperation {
    UPSERT,
    SOFT_DELETE,
}

enum class SyncOutboxStatus {
    PENDING,
    IN_FLIGHT,
    FAILED,
    CONFLICT,
    DONE,
}

