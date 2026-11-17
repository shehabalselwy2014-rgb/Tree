-- ============================================================================
-- Juthoor (جِذور) Row-Level Security (RLS), Branch Authorization Helpers,
-- Atomic Invitation Acceptance RPC, and Optimistic Concurrency Controls
-- Migration 0002
-- ============================================================================

-- Helper: returns active member row for current authenticated user in a family
CREATE OR REPLACE FUNCTION public.get_active_member(p_family_id UUID)
RETURNS public.family_members
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT fm.*
    FROM public.family_members fm
    WHERE fm.family_id = p_family_id
      AND fm.user_id = auth.uid()
      AND fm.status = 'ACTIVE'
    LIMIT 1;
$$;

CREATE OR REPLACE FUNCTION public.is_active_family_member(p_family_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.family_members fm
        WHERE fm.family_id = p_family_id
          AND fm.user_id = auth.uid()
          AND fm.status = 'ACTIVE'
    );
$$;

CREATE OR REPLACE FUNCTION public.is_family_owner(p_family_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.family_members fm
        WHERE fm.family_id = p_family_id
          AND fm.user_id = auth.uid()
          AND fm.status = 'ACTIVE'
          AND fm.role = 'OWNER'
    );
$$;

CREATE OR REPLACE FUNCTION public.is_family_admin_or_owner(p_family_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.family_members fm
        WHERE fm.family_id = p_family_id
          AND fm.user_id = auth.uid()
          AND fm.status = 'ACTIVE'
          AND fm.role IN ('OWNER', 'ADMIN')
    );
$$;

-- Checks whether target person lies within the member's authorized subtree
-- (branch_root_person_id or linked_person_id, their descendants, and spouses of those members)
CREATE OR REPLACE FUNCTION public.is_person_within_member_branch(
    p_family_id UUID,
    p_target_person_id UUID
)
RETURNS BOOLEAN
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_member public.family_members;
    v_root_id UUID;
    v_allowed BOOLEAN;
BEGIN
    SELECT * INTO v_member FROM public.get_active_member(p_family_id);
    IF v_member.id IS NULL THEN
        RETURN FALSE;
    END IF;

    IF v_member.role IN ('OWNER', 'ADMIN') THEN
        RETURN TRUE;
    END IF;

    v_root_id := COALESCE(v_member.branch_root_person_id, v_member.linked_person_id);
    IF v_root_id IS NULL THEN
        RETURN FALSE;
    END IF;

    WITH RECURSIVE branch_descendants AS (
        SELECT v_root_id AS person_id
        UNION
        SELECT pcr.child_id
        FROM public.parent_child_relationships pcr
        JOIN branch_descendants bd ON pcr.parent_id = bd.person_id
        WHERE pcr.family_id = p_family_id AND pcr.is_deleted = FALSE
    ),
    branch_with_spouses AS (
        SELECT person_id FROM branch_descendants
        UNION
        SELECT CASE
            WHEN m.partner_one_id = bd.person_id THEN m.partner_two_id
            ELSE m.partner_one_id
        END AS person_id
        FROM public.marriages m
        JOIN branch_descendants bd ON m.partner_one_id = bd.person_id OR m.partner_two_id = bd.person_id
        WHERE m.family_id = p_family_id AND m.is_deleted = FALSE
    )
    SELECT EXISTS (SELECT 1 FROM branch_with_spouses WHERE person_id = p_target_person_id)
    INTO v_allowed;

    RETURN COALESCE(v_allowed, FALSE);
END;
$$;

CREATE OR REPLACE FUNCTION public.can_edit_person_in_family(
    p_family_id UUID,
    p_target_person_id UUID
)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.family_members fm
        WHERE fm.family_id = p_family_id
          AND fm.user_id = auth.uid()
          AND fm.status = 'ACTIVE'
          AND (
              fm.role IN ('OWNER', 'ADMIN')
              OR (
                  fm.role = 'BRANCH_MANAGER'
                  AND public.is_person_within_member_branch(p_family_id, p_target_person_id)
              )
          )
    );
$$;

CREATE OR REPLACE FUNCTION public.can_create_in_family(p_family_id UUID)
RETURNS BOOLEAN
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (
        SELECT 1 FROM public.family_members fm
        WHERE fm.family_id = p_family_id
          AND fm.user_id = auth.uid()
          AND fm.status = 'ACTIVE'
          AND fm.role IN ('OWNER', 'ADMIN', 'BRANCH_MANAGER')
    );
$$;

-- ============================================================================
-- Enable Row-Level Security on All Tables
-- ============================================================================
ALTER TABLE public.user_profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.families ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.family_groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.persons ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.family_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.family_invitations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.marriages ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.parent_child_relationships ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.education ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.person_photos ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.photo_person_tags ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.events ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.audit_logs ENABLE ROW LEVEL SECURITY;

-- User Profiles: users manage their own profile; active co-members can read display names
CREATE POLICY user_profiles_select_self_or_family ON public.user_profiles
    FOR SELECT USING (
        id = auth.uid()
        OR EXISTS (
            SELECT 1 FROM public.family_members me
            JOIN public.family_members them ON them.family_id = me.family_id
            WHERE me.user_id = auth.uid() AND me.status = 'ACTIVE'
              AND them.user_id = public.user_profiles.id
        )
    );

CREATE POLICY user_profiles_insert_self ON public.user_profiles
    FOR INSERT WITH CHECK (id = auth.uid());

CREATE POLICY user_profiles_update_self ON public.user_profiles
    FOR UPDATE USING (id = auth.uid()) WITH CHECK (id = auth.uid());

-- Families
CREATE POLICY families_select_member ON public.families
    FOR SELECT USING (public.is_active_family_member(id));

CREATE POLICY families_insert_owner ON public.families
    FOR INSERT WITH CHECK (owner_user_id = auth.uid());

CREATE POLICY families_update_admin_or_owner ON public.families
    FOR UPDATE USING (public.is_family_admin_or_owner(id))
    WITH CHECK (public.is_family_admin_or_owner(id));

-- Family Members
CREATE POLICY family_members_select ON public.family_members
    FOR SELECT USING (public.is_active_family_member(family_id) OR user_id = auth.uid());

CREATE POLICY family_members_insert_admin ON public.family_members
    FOR INSERT WITH CHECK (
        public.is_family_admin_or_owner(family_id)
        AND role <> 'OWNER'
    );

CREATE POLICY family_members_update_owner_or_admin ON public.family_members
    FOR UPDATE USING (
        public.is_family_owner(family_id)
        OR (public.is_family_admin_or_owner(family_id) AND role <> 'OWNER')
    )
    WITH CHECK (
        public.is_family_owner(family_id)
        OR (public.is_family_admin_or_owner(family_id) AND role <> 'OWNER')
    );

-- Family Invitations
CREATE POLICY invitations_select_authorized ON public.family_invitations
    FOR SELECT USING (
        public.is_family_admin_or_owner(family_id)
        OR EXISTS (
            SELECT 1 FROM public.family_members fm
            WHERE fm.family_id = family_invitations.family_id
              AND fm.user_id = auth.uid()
              AND fm.status = 'ACTIVE'
              AND fm.can_invite = TRUE
        )
    );

CREATE POLICY invitations_insert_authorized ON public.family_invitations
    FOR INSERT WITH CHECK (
        created_by_user_id = auth.uid()
        AND (
            public.is_family_owner(family_id)
            OR (
                public.is_family_admin_or_owner(family_id)
                AND assigned_role <> 'ADMIN'
            )
        )
    );

CREATE POLICY invitations_update_authorized ON public.family_invitations
    FOR UPDATE USING (public.is_family_admin_or_owner(family_id))
    WITH CHECK (public.is_family_admin_or_owner(family_id));

-- Persons (RESTRICTED_MEMBER visibility respects branch scope or non-deleted core records)
CREATE POLICY persons_select_member ON public.persons
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM public.family_members fm
            WHERE fm.family_id = persons.family_id
              AND fm.user_id = auth.uid()
              AND fm.status = 'ACTIVE'
              AND (
                  fm.role <> 'RESTRICTED_MEMBER'
                  OR public.is_person_within_member_branch(persons.family_id, persons.id)
              )
        )
    );

CREATE POLICY persons_insert_authorized ON public.persons
    FOR INSERT WITH CHECK (public.can_create_in_family(family_id));

CREATE POLICY persons_update_authorized ON public.persons
    FOR UPDATE USING (public.can_edit_person_in_family(family_id, id))
    WITH CHECK (public.can_edit_person_in_family(family_id, id));

-- Marriages
CREATE POLICY marriages_select_member ON public.marriages
    FOR SELECT USING (public.is_active_family_member(family_id));

CREATE POLICY marriages_insert_authorized ON public.marriages
    FOR INSERT WITH CHECK (
        public.can_edit_person_in_family(family_id, partner_one_id)
        OR public.can_edit_person_in_family(family_id, partner_two_id)
    );

CREATE POLICY marriages_update_authorized ON public.marriages
    FOR UPDATE USING (
        public.can_edit_person_in_family(family_id, partner_one_id)
        OR public.can_edit_person_in_family(family_id, partner_two_id)
    );

-- Parent-Child Relationships
CREATE POLICY parent_child_select_member ON public.parent_child_relationships
    FOR SELECT USING (public.is_active_family_member(family_id));

CREATE POLICY parent_child_insert_authorized ON public.parent_child_relationships
    FOR INSERT WITH CHECK (
        public.can_edit_person_in_family(family_id, parent_id)
        OR public.can_edit_person_in_family(family_id, child_id)
    );

CREATE POLICY parent_child_update_authorized ON public.parent_child_relationships
    FOR UPDATE USING (
        public.can_edit_person_in_family(family_id, parent_id)
        OR public.can_edit_person_in_family(family_id, child_id)
    );

-- Education, Photos, Tags, Events, Family Groups
CREATE POLICY education_select_member ON public.education
    FOR SELECT USING (public.is_active_family_member(family_id));
CREATE POLICY education_write_authorized ON public.education
    FOR ALL USING (public.can_edit_person_in_family(family_id, person_id))
    WITH CHECK (public.can_edit_person_in_family(family_id, person_id));

CREATE POLICY events_select_member ON public.events
    FOR SELECT USING (public.is_active_family_member(family_id));
CREATE POLICY events_write_authorized ON public.events
    FOR ALL USING (
        CASE WHEN person_id IS NULL THEN public.is_family_admin_or_owner(family_id)
             ELSE public.can_edit_person_in_family(family_id, person_id) END
    )
    WITH CHECK (
        CASE WHEN person_id IS NULL THEN public.is_family_admin_or_owner(family_id)
             ELSE public.can_edit_person_in_family(family_id, person_id) END
    );

CREATE POLICY photos_select_member ON public.person_photos
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM public.family_members fm
            WHERE fm.family_id = person_photos.family_id
              AND fm.user_id = auth.uid()
              AND fm.status = 'ACTIVE'
              AND (
                  fm.role <> 'RESTRICTED_MEMBER'
                  OR (person_photos.person_id IS NOT NULL AND public.is_person_within_member_branch(person_photos.family_id, person_photos.person_id))
              )
        )
    );

CREATE POLICY photos_write_authorized ON public.person_photos
    FOR ALL USING (
        public.is_family_admin_or_owner(family_id)
        OR (person_id IS NOT NULL AND public.can_edit_person_in_family(family_id, person_id))
    )
    WITH CHECK (
        public.is_family_admin_or_owner(family_id)
        OR (person_id IS NOT NULL AND public.can_edit_person_in_family(family_id, person_id))
    );

CREATE POLICY photo_tags_select_member ON public.photo_person_tags
    FOR SELECT USING (public.is_active_family_member(family_id));
CREATE POLICY photo_tags_write_authorized ON public.photo_person_tags
    FOR ALL USING (public.can_edit_person_in_family(family_id, person_id))
    WITH CHECK (public.can_edit_person_in_family(family_id, person_id));

CREATE POLICY family_groups_select_member ON public.family_groups
    FOR SELECT USING (public.is_active_family_member(family_id));
CREATE POLICY family_groups_write_admin ON public.family_groups
    FOR ALL USING (public.is_family_admin_or_owner(family_id))
    WITH CHECK (public.is_family_admin_or_owner(family_id));

-- Audit Logs (Readable by active members, appendable only by authenticated members for their own actor_user_id)
CREATE POLICY audit_logs_select_member ON public.audit_logs
    FOR SELECT USING (public.is_active_family_member(family_id));

CREATE POLICY audit_logs_insert_actor ON public.audit_logs
    FOR INSERT WITH CHECK (
        public.is_active_family_member(family_id)
        AND actor_user_id = auth.uid()
    );

-- ============================================================================
-- Atomic RPC: Accept Family Invitation (Enforces auth, expiry, max_uses, and 1:1 person link)
-- ============================================================================
CREATE OR REPLACE FUNCTION public.accept_family_invitation(p_invite_code TEXT)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_now BIGINT := (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT;
    v_code_hash TEXT;
    v_inv public.family_invitations;
    v_member_id UUID;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required to accept invitation';
    END IF;

    v_code_hash := encode(digest(trim(p_invite_code), 'sha256'), 'hex');

    SELECT * INTO v_inv
    FROM public.family_invitations
    WHERE invite_code_hash = v_code_hash
    FOR UPDATE;

    IF v_inv.id IS NULL THEN
        RAISE EXCEPTION 'Invalid invitation code';
    END IF;

    IF v_inv.status <> 'PENDING' THEN
        RAISE EXCEPTION 'Invitation is no longer active (status: %)', v_inv.status;
    END IF;

    IF v_inv.expires_at < v_now THEN
        UPDATE public.family_invitations
        SET status = 'EXPIRED', updated_at = v_now
        WHERE id = v_inv.id;
        RAISE EXCEPTION 'Invitation has expired';
    END IF;

    IF v_inv.uses_count >= v_inv.max_uses THEN
        UPDATE public.family_invitations
        SET status = 'ACCEPTED', updated_at = v_now
        WHERE id = v_inv.id;
        RAISE EXCEPTION 'Invitation usage limit reached';
    END IF;

    -- Prevent hijacking a Person record already bound to another active member
    IF v_inv.target_person_id IS NOT NULL AND EXISTS (
        SELECT 1 FROM public.family_members fm
        WHERE fm.family_id = v_inv.family_id
          AND fm.linked_person_id = v_inv.target_person_id
          AND fm.status = 'ACTIVE'
          AND fm.user_id <> v_uid
    ) THEN
        RAISE EXCEPTION 'Target person record is already linked to another active account';
    END IF;

    INSERT INTO public.family_members (
        family_id,
        user_id,
        linked_person_id,
        role,
        branch_root_person_id,
        can_invite,
        can_export,
        can_manage_media,
        status,
        invited_by_user_id,
        joined_at,
        updated_at,
        version
    ) VALUES (
        v_inv.family_id,
        v_uid,
        v_inv.target_person_id,
        v_inv.assigned_role,
        COALESCE(v_inv.branch_root_person_id, v_inv.target_person_id),
        (v_inv.assigned_role IN ('ADMIN')),
        (v_inv.assigned_role <> 'RESTRICTED_MEMBER'),
        (v_inv.assigned_role IN ('ADMIN', 'BRANCH_MANAGER')),
        'ACTIVE',
        v_inv.created_by_user_id,
        v_now,
        v_now,
        1
    )
    ON CONFLICT (family_id, user_id) DO UPDATE SET
        linked_person_id = COALESCE(EXCLUDED.linked_person_id, family_members.linked_person_id),
        role = EXCLUDED.role,
        branch_root_person_id = EXCLUDED.branch_root_person_id,
        status = 'ACTIVE',
        updated_at = v_now,
        version = family_members.version + 1
    RETURNING id INTO v_member_id;

    UPDATE public.family_invitations
    SET uses_count = uses_count + 1,
        accepted_by_user_id = v_uid,
        status = CASE WHEN uses_count + 1 >= max_uses THEN 'ACCEPTED' ELSE 'PENDING' END,
        updated_at = v_now
    WHERE id = v_inv.id;

    INSERT INTO public.audit_logs (
        family_id, actor_user_id, actor_member_role, entity_type, entity_id, operation, old_value, new_value, operation_date
    ) VALUES (
        v_inv.family_id, v_uid, v_inv.assigned_role, 'FamilyMembers', v_member_id::TEXT, 'CREATE', NULL,
        'Accepted invitation linked_person=' || COALESCE(v_inv.target_person_id::TEXT, 'NONE'), v_now
    );

    RETURN jsonb_build_object(
        'family_id', v_inv.family_id,
        'member_id', v_member_id,
        'linked_person_id', v_inv.target_person_id,
        'role', v_inv.assigned_role
    );
END;
$$;

-- ============================================================================
-- Private Storage Bucket RLS Policy Template for 'family-media'
-- (Objects stored under path: <family_uuid>/<photo_uuid>.<ext>)
-- ============================================================================
INSERT INTO storage.buckets (id, name, public)
VALUES ('family-media', 'family-media', FALSE)
ON CONFLICT (id) DO NOTHING;

CREATE POLICY family_media_select_active_member ON storage.objects
    FOR SELECT TO authenticated
    USING (
        bucket_id = 'family-media'
        AND public.is_active_family_member((storage.foldername(name))[1]::UUID)
    );

CREATE POLICY family_media_insert_authorized ON storage.objects
    FOR INSERT TO authenticated
    WITH CHECK (
        bucket_id = 'family-media'
        AND public.can_create_in_family((storage.foldername(name))[1]::UUID)
    );

CREATE POLICY family_media_delete_authorized ON storage.objects
    FOR DELETE TO authenticated
    USING (
        bucket_id = 'family-media'
        AND public.is_family_admin_or_owner((storage.foldername(name))[1]::UUID)
    );
