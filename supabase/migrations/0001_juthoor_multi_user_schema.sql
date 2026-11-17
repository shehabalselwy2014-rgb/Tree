-- ============================================================================
-- Juthoor (جِذور) Multi-User Central Schema for PostgreSQL / Supabase
-- Migration 0001: Core Relational Tables, Constraints & Genealogy Invariants
-- Preserves strict separation between Auth User Identity (user_profiles)
-- and Family Tree Identity (persons).
-- ============================================================================

CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. User Profiles (separated from Persons in the genealogy tree)
CREATE TABLE IF NOT EXISTS public.user_profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT NOT NULL UNIQUE,
    display_name TEXT NOT NULL,
    avatar_storage_key TEXT,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

-- 2. Families (Top-level multi-user workspaces, distinct from internal family_groups branches)
CREATE TABLE IF NOT EXISTS public.families (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL CHECK (char_length(trim(name)) > 0),
    motto TEXT NOT NULL DEFAULT 'نوثّق الأصول، ونحفظ امتداد الأجيال',
    owner_user_id UUID NOT NULL REFERENCES public.user_profiles(id) ON DELETE RESTRICT,
    root_person_id UUID,
    version BIGINT NOT NULL DEFAULT 1,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at BIGINT,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

CREATE INDEX IF NOT EXISTS idx_families_owner_user_id ON public.families(owner_user_id);

-- 3. Family Groups (Internal family branches within a Family workspace)
CREATE TABLE IF NOT EXISTS public.family_groups (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES public.families(id) ON DELETE CASCADE,
    group_name TEXT NOT NULL CHECK (char_length(trim(group_name)) > 0),
    description TEXT,
    root_person_id UUID,
    created_by_user_id UUID REFERENCES public.user_profiles(id) ON DELETE SET NULL,
    updated_by_user_id UUID REFERENCES public.user_profiles(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 1,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at BIGINT,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

CREATE INDEX IF NOT EXISTS idx_family_groups_family_id ON public.family_groups(family_id);
CREATE INDEX IF NOT EXISTS idx_family_groups_root_person_id ON public.family_groups(root_person_id);

-- 4. Persons (Genealogy nodes with stable UUIDs, strictly decoupled from user_profiles)
CREATE TABLE IF NOT EXISTS public.persons (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES public.families(id) ON DELETE CASCADE,
    full_name TEXT NOT NULL CHECK (char_length(trim(full_name)) > 0),
    first_name TEXT NOT NULL CHECK (char_length(trim(first_name)) > 0),
    middle_name TEXT,
    family_name TEXT,
    gender TEXT NOT NULL CHECK (gender IN ('MALE', 'FEMALE')),
    birth_date TEXT,
    birth_date_precision TEXT NOT NULL DEFAULT 'UNKNOWN'
        CHECK (birth_date_precision IN ('UNKNOWN', 'EXACT', 'MONTH_YEAR', 'YEAR_ONLY', 'APPROXIMATE')),
    birth_place TEXT,
    death_date TEXT,
    death_date_precision TEXT NOT NULL DEFAULT 'UNKNOWN'
        CHECK (death_date_precision IN ('UNKNOWN', 'EXACT', 'MONTH_YEAR', 'YEAR_ONLY', 'APPROXIMATE')),
    death_place TEXT,
    biography TEXT,
    occupation TEXT,
    current_marital_status TEXT NOT NULL DEFAULT 'SINGLE'
        CHECK (current_marital_status IN ('SINGLE', 'MARRIED', 'DIVORCED', 'WIDOWED', 'UNKNOWN')),
    remote_avatar_key TEXT,
    avatar_sha256 TEXT,
    family_group_id UUID REFERENCES public.family_groups(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED,
    notes TEXT,
    created_by_user_id UUID REFERENCES public.user_profiles(id) ON DELETE SET NULL,
    updated_by_user_id UUID REFERENCES public.user_profiles(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 1,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at BIGINT,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

CREATE INDEX IF NOT EXISTS idx_persons_family_id ON public.persons(family_id);
CREATE INDEX IF NOT EXISTS idx_persons_family_updated ON public.persons(family_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_persons_family_group_id ON public.persons(family_group_id);
CREATE INDEX IF NOT EXISTS idx_persons_full_name ON public.persons(family_id, full_name);

-- Deferred FKs from families / family_groups -> persons
ALTER TABLE public.families
    ADD CONSTRAINT fk_families_root_person
    FOREIGN KEY (root_person_id) REFERENCES public.persons(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED;

ALTER TABLE public.family_groups
    ADD CONSTRAINT fk_family_groups_root_person
    FOREIGN KEY (root_person_id) REFERENCES public.persons(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED;

-- 5. Family Members (Authorization & Role Mapping between user_profiles and families/persons)
CREATE TABLE IF NOT EXISTS public.family_members (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES public.families(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES public.user_profiles(id) ON DELETE CASCADE,
    linked_person_id UUID REFERENCES public.persons(id) ON DELETE SET NULL,
    role TEXT NOT NULL CHECK (role IN ('OWNER', 'ADMIN', 'BRANCH_MANAGER', 'MEMBER', 'RESTRICTED_MEMBER')),
    branch_root_person_id UUID REFERENCES public.persons(id) ON DELETE SET NULL,
    can_invite BOOLEAN NOT NULL DEFAULT FALSE,
    can_export BOOLEAN NOT NULL DEFAULT TRUE,
    can_manage_media BOOLEAN NOT NULL DEFAULT FALSE,
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'REVOKED')),
    invited_by_user_id UUID REFERENCES public.user_profiles(id) ON DELETE SET NULL,
    joined_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    version BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT uq_family_member_user UNIQUE (family_id, user_id)
);

-- Guarantees one Person record in the family tree cannot be claimed by two active user accounts
CREATE UNIQUE INDEX IF NOT EXISTS uq_family_member_active_person
    ON public.family_members(family_id, linked_person_id)
    WHERE linked_person_id IS NOT NULL AND status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_family_members_user_id ON public.family_members(user_id);
CREATE INDEX IF NOT EXISTS idx_family_members_family_status ON public.family_members(family_id, status);

-- 6. Family Invitations (Secure single-use or bounded invitations linked to an existing Person)
CREATE TABLE IF NOT EXISTS public.family_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES public.families(id) ON DELETE CASCADE,
    target_person_id UUID REFERENCES public.persons(id) ON DELETE SET NULL,
    assigned_role TEXT NOT NULL CHECK (assigned_role IN ('ADMIN', 'BRANCH_MANAGER', 'MEMBER', 'RESTRICTED_MEMBER')),
    branch_root_person_id UUID REFERENCES public.persons(id) ON DELETE SET NULL,
    invite_code_hash TEXT NOT NULL UNIQUE,
    invite_code_hint TEXT NOT NULL,
    max_uses INT NOT NULL DEFAULT 1 CHECK (max_uses >= 1 AND max_uses <= 10),
    uses_count INT NOT NULL DEFAULT 0 CHECK (uses_count >= 0 AND uses_count <= max_uses),
    expires_at BIGINT NOT NULL,
    status TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'REVOKED', 'EXPIRED')),
    created_by_user_id UUID NOT NULL REFERENCES public.user_profiles(id) ON DELETE CASCADE,
    accepted_by_user_id UUID REFERENCES public.user_profiles(id) ON DELETE SET NULL,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

CREATE INDEX IF NOT EXISTS idx_family_invitations_family ON public.family_invitations(family_id, status);
CREATE INDEX IF NOT EXISTS idx_family_invitations_target_person ON public.family_invitations(target_person_id);

-- 7. Marriages (Preserves polygyny & historical divorced/ended marriages)
CREATE TABLE IF NOT EXISTS public.marriages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES public.families(id) ON DELETE CASCADE,
    partner_one_id UUID NOT NULL REFERENCES public.persons(id) ON DELETE CASCADE,
    partner_two_id UUID NOT NULL REFERENCES public.persons(id) ON DELETE CASCADE,
    marriage_date TEXT,
    marriage_date_precision TEXT NOT NULL DEFAULT 'UNKNOWN'
        CHECK (marriage_date_precision IN ('UNKNOWN', 'EXACT', 'MONTH_YEAR', 'YEAR_ONLY', 'APPROXIMATE')),
    marriage_place TEXT,
    marriage_status TEXT NOT NULL DEFAULT 'ACTIVE'
        CHECK (marriage_status IN ('ACTIVE', 'DIVORCED', 'ENDED_BY_DEATH')),
    end_date TEXT,
    end_reason TEXT,
    notes TEXT,
    created_by_user_id UUID REFERENCES public.user_profiles(id) ON DELETE SET NULL,
    updated_by_user_id UUID REFERENCES public.user_profiles(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 1,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at BIGINT,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    CONSTRAINT chk_marriage_distinct_partners CHECK (partner_one_id <> partner_two_id)
);

CREATE INDEX IF NOT EXISTS idx_marriages_family_id ON public.marriages(family_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_marriages_partner_one ON public.marriages(partner_one_id);
CREATE INDEX IF NOT EXISTS idx_marriages_partner_two ON public.marriages(partner_two_id);

-- 8. Parent-Child Relationships (Normalized lineage bound optionally to specific Marriage)
CREATE TABLE IF NOT EXISTS public.parent_child_relationships (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES public.families(id) ON DELETE CASCADE,
    parent_id UUID NOT NULL REFERENCES public.persons(id) ON DELETE CASCADE,
    child_id UUID NOT NULL REFERENCES public.persons(id) ON DELETE CASCADE,
    relationship_type TEXT NOT NULL CHECK (
        relationship_type IN (
            'BIOLOGICAL_FATHER',
            'BIOLOGICAL_MOTHER',
            'ADOPTIVE_GUARDIAN',
            'UNCONFIRMED_FATHER',
            'UNCONFIRMED_MOTHER'
        )
    ),
    marriage_id UUID REFERENCES public.marriages(id) ON DELETE SET NULL,
    notes TEXT,
    created_by_user_id UUID REFERENCES public.user_profiles(id) ON DELETE SET NULL,
    updated_by_user_id UUID REFERENCES public.user_profiles(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 1,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at BIGINT,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    CONSTRAINT chk_parent_child_not_self CHECK (parent_id <> child_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_active_parent_child_rel
    ON public.parent_child_relationships(parent_id, child_id, relationship_type)
    WHERE is_deleted = FALSE;

CREATE INDEX IF NOT EXISTS idx_parent_child_family ON public.parent_child_relationships(family_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_parent_child_parent ON public.parent_child_relationships(parent_id);
CREATE INDEX IF NOT EXISTS idx_parent_child_child ON public.parent_child_relationships(child_id);

-- 9. Education Records
CREATE TABLE IF NOT EXISTS public.education (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES public.families(id) ON DELETE CASCADE,
    person_id UUID NOT NULL REFERENCES public.persons(id) ON DELETE CASCADE,
    qualification TEXT NOT NULL CHECK (char_length(trim(qualification)) > 0),
    specialization TEXT,
    institution TEXT,
    graduation_year INT,
    notes TEXT,
    created_by_user_id UUID REFERENCES public.user_profiles(id) ON DELETE SET NULL,
    updated_by_user_id UUID REFERENCES public.user_profiles(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 1,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at BIGINT,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

CREATE INDEX IF NOT EXISTS idx_education_family_person ON public.education(family_id, person_id);

-- 10. Person Photos & Documents + Multi-person Tags
CREATE TABLE IF NOT EXISTS public.person_photos (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES public.families(id) ON DELETE CASCADE,
    person_id UUID REFERENCES public.persons(id) ON DELETE SET NULL,
    remote_storage_key TEXT NOT NULL,
    sha256 TEXT,
    description TEXT,
    photo_date TEXT,
    photo_date_precision TEXT NOT NULL DEFAULT 'UNKNOWN'
        CHECK (photo_date_precision IN ('UNKNOWN', 'EXACT', 'MONTH_YEAR', 'YEAR_ONLY', 'APPROXIMATE')),
    media_type TEXT NOT NULL DEFAULT 'PERSON_PORTRAIT'
        CHECK (media_type IN ('PERSON_PORTRAIT', 'GROUP_PHOTO', 'ANCESTOR_PHOTO', 'HISTORICAL_PHOTO', 'FAMILY_DOCUMENT')),
    created_by_user_id UUID REFERENCES public.user_profiles(id) ON DELETE SET NULL,
    updated_by_user_id UUID REFERENCES public.user_profiles(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 1,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at BIGINT,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

CREATE INDEX IF NOT EXISTS idx_person_photos_family ON public.person_photos(family_id, updated_at);
CREATE INDEX IF NOT EXISTS idx_person_photos_person ON public.person_photos(person_id);

CREATE TABLE IF NOT EXISTS public.photo_person_tags (
    photo_id UUID NOT NULL REFERENCES public.person_photos(id) ON DELETE CASCADE,
    person_id UUID NOT NULL REFERENCES public.persons(id) ON DELETE CASCADE,
    family_id UUID NOT NULL REFERENCES public.families(id) ON DELETE CASCADE,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    PRIMARY KEY (photo_id, person_id)
);

CREATE INDEX IF NOT EXISTS idx_photo_person_tags_person ON public.photo_person_tags(person_id);

-- 11. Family Events
CREATE TABLE IF NOT EXISTS public.events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES public.families(id) ON DELETE CASCADE,
    person_id UUID REFERENCES public.persons(id) ON DELETE CASCADE,
    event_type TEXT NOT NULL CHECK (
        event_type IN ('BIRTH', 'MARRIAGE', 'DEATH', 'GRADUATION', 'MEMORIAL', 'FAMILY_GATHERING', 'OTHER')
    ),
    event_date TEXT,
    event_date_precision TEXT NOT NULL DEFAULT 'UNKNOWN'
        CHECK (event_date_precision IN ('UNKNOWN', 'EXACT', 'MONTH_YEAR', 'YEAR_ONLY', 'APPROXIMATE')),
    event_place TEXT,
    description TEXT,
    created_by_user_id UUID REFERENCES public.user_profiles(id) ON DELETE SET NULL,
    updated_by_user_id UUID REFERENCES public.user_profiles(id) ON DELETE SET NULL,
    version BIGINT NOT NULL DEFAULT 1,
    is_deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at BIGINT,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

CREATE INDEX IF NOT EXISTS idx_events_family_person ON public.events(family_id, person_id);

-- 12. Audit Logs (Append-only family administrative & genealogy activity trail)
CREATE TABLE IF NOT EXISTS public.audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    family_id UUID NOT NULL REFERENCES public.families(id) ON DELETE CASCADE,
    actor_user_id UUID REFERENCES public.user_profiles(id) ON DELETE SET NULL,
    actor_member_role TEXT,
    entity_type TEXT NOT NULL,
    entity_id TEXT NOT NULL,
    operation TEXT NOT NULL CHECK (operation IN ('CREATE', 'UPDATE', 'DELETE', 'RESTORE')),
    old_value TEXT,
    new_value TEXT,
    operation_date BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_family_date ON public.audit_logs(family_id, operation_date DESC);

-- ============================================================================
-- Server-side Genealogy Integrity Triggers (Protects against concurrent offline merges)
-- ============================================================================

-- Prevent cycles in parent_child_relationships even across simultaneous offline device syncs
CREATE OR REPLACE FUNCTION public.fn_validate_parent_child_dag()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_cycle_found BOOLEAN;
    v_existing_bio_parent BOOLEAN;
BEGIN
    IF NEW.is_deleted THEN
        RETURN NEW;
    END IF;

    -- Ensure parent and child belong to the same family
    IF NOT EXISTS (
        SELECT 1 FROM public.persons p1
        JOIN public.persons p2 ON p2.id = NEW.child_id
        WHERE p1.id = NEW.parent_id AND p1.family_id = NEW.family_id AND p2.family_id = NEW.family_id
    ) THEN
        RAISE EXCEPTION 'Parent and child must belong to the same family';
    END IF;

    -- Prevent two biological fathers or two biological mothers on concurrent merge
    IF NEW.relationship_type IN ('BIOLOGICAL_FATHER', 'BIOLOGICAL_MOTHER') THEN
        SELECT EXISTS (
            SELECT 1 FROM public.parent_child_relationships r
            WHERE r.child_id = NEW.child_id
              AND r.relationship_type = NEW.relationship_type
              AND r.is_deleted = FALSE
              AND r.id <> NEW.id
        ) INTO v_existing_bio_parent;

        IF v_existing_bio_parent THEN
            RAISE EXCEPTION 'Child already has a confirmed %', NEW.relationship_type;
        END IF;
    END IF;

    -- Cycle check: if child is already an ancestor of parent, reject parent -> child
    WITH RECURSIVE ancestors AS (
        SELECT parent_id AS ancestor_id
        FROM public.parent_child_relationships
        WHERE child_id = NEW.parent_id AND is_deleted = FALSE AND id <> NEW.id
        UNION
        SELECT r.parent_id
        FROM public.parent_child_relationships r
        JOIN ancestors a ON r.child_id = a.ancestor_id
        WHERE r.is_deleted = FALSE AND r.id <> NEW.id
    )
    SELECT EXISTS (SELECT 1 FROM ancestors WHERE ancestor_id = NEW.child_id)
    INTO v_cycle_found;

    IF v_cycle_found THEN
        RAISE EXCEPTION 'Cycle detected: descendant cannot become ancestor of parent';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_parent_child_dag ON public.parent_child_relationships;
CREATE TRIGGER trg_validate_parent_child_dag
    BEFORE INSERT OR UPDATE ON public.parent_child_relationships
    FOR EACH ROW EXECUTE FUNCTION public.fn_validate_parent_child_dag();

-- Enforce Islamic marriage integrity rules on server (opposite genders, max 4 active wives, max 1 active husband)
CREATE OR REPLACE FUNCTION public.fn_validate_islamic_marriage()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    v_g1 TEXT;
    v_g2 TEXT;
    v_husband_id UUID;
    v_wife_id UUID;
    v_husband_active_count INT;
    v_wife_active_exists BOOLEAN;
BEGIN
    IF NEW.is_deleted THEN
        RETURN NEW;
    END IF;

    SELECT gender INTO v_g1 FROM public.persons WHERE id = NEW.partner_one_id AND family_id = NEW.family_id;
    SELECT gender INTO v_g2 FROM public.persons WHERE id = NEW.partner_two_id AND family_id = NEW.family_id;

    IF v_g1 IS NULL OR v_g2 IS NULL THEN
        RAISE EXCEPTION 'Marriage partners must exist in the same family';
    END IF;

    IF v_g1 = v_g2 THEN
        RAISE EXCEPTION 'Marriage partners must have complementary genders (MALE and FEMALE)';
    END IF;

    IF NEW.marriage_status = 'ACTIVE' THEN
        v_husband_id := CASE WHEN v_g1 = 'MALE' THEN NEW.partner_one_id ELSE NEW.partner_two_id END;
        v_wife_id := CASE WHEN v_g1 = 'FEMALE' THEN NEW.partner_one_id ELSE NEW.partner_two_id END;

        SELECT COUNT(*) INTO v_husband_active_count
        FROM public.marriages m
        WHERE m.family_id = NEW.family_id
          AND m.is_deleted = FALSE
          AND m.marriage_status = 'ACTIVE'
          AND m.id <> NEW.id
          AND (m.partner_one_id = v_husband_id OR m.partner_two_id = v_husband_id);

        IF v_husband_active_count >= 4 THEN
            RAISE EXCEPTION 'Husband cannot exceed 4 concurrent active marriages';
        END IF;

        SELECT EXISTS (
            SELECT 1 FROM public.marriages m
            WHERE m.family_id = NEW.family_id
              AND m.is_deleted = FALSE
              AND m.marriage_status = 'ACTIVE'
              AND m.id <> NEW.id
              AND (m.partner_one_id = v_wife_id OR m.partner_two_id = v_wife_id)
        ) INTO v_wife_active_exists;

        IF v_wife_active_exists THEN
            RAISE EXCEPTION 'Wife already has an active marriage';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_validate_islamic_marriage ON public.marriages;
CREATE TRIGGER trg_validate_islamic_marriage
    BEFORE INSERT OR UPDATE ON public.marriages
    FOR EACH ROW EXECUTE FUNCTION public.fn_validate_islamic_marriage();
