-- ============================================================================
-- Juthoor (جِذور) Invitations, Join-by-Code, and Member-to-Person Linking RPCs
-- Migration 0004
-- ============================================================================

-- Support referencing locally created Person UUIDs before Phase 9.5 sync pushes persons
ALTER TABLE public.family_invitations
    ADD COLUMN IF NOT EXISTS pending_target_person_id UUID,
    ADD COLUMN IF NOT EXISTS pending_branch_root_person_id UUID;

-- Normalize invite code consistently and allow safe server-side invitation creation
CREATE OR REPLACE FUNCTION public.create_family_invitation(
    p_family_id UUID,
    p_target_person_id UUID DEFAULT NULL,
    p_assigned_role TEXT DEFAULT 'BRANCH_MANAGER',
    p_branch_root_person_id UUID DEFAULT NULL,
    p_invite_code_hash TEXT DEFAULT '',
    p_invite_code_hint TEXT DEFAULT '',
    p_max_uses INT DEFAULT 1,
    p_expires_at BIGINT DEFAULT 0
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_now BIGINT := (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT;
    v_caller public.family_members;
    v_resolved_target UUID;
    v_pending_target UUID;
    v_resolved_branch UUID;
    v_pending_branch UUID;
    v_inv_id UUID;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT * INTO v_caller FROM public.get_active_member(p_family_id);
    IF v_caller.id IS NULL THEN
        RAISE EXCEPTION 'Not authorized: active family membership required';
    END IF;

    IF v_caller.role NOT IN ('OWNER', 'ADMIN') AND NOT v_caller.can_invite THEN
        RAISE EXCEPTION 'Not authorized to create invitations';
    END IF;

    IF p_assigned_role NOT IN ('ADMIN', 'BRANCH_MANAGER', 'MEMBER', 'RESTRICTED_MEMBER') THEN
        RAISE EXCEPTION 'Invalid invitation role: %', p_assigned_role;
    END IF;

    IF p_assigned_role = 'ADMIN' AND v_caller.role <> 'OWNER' THEN
        RAISE EXCEPTION 'Only family owner can invite an ADMIN';
    END IF;

    IF p_max_uses < 1 OR p_max_uses > 10 THEN
        RAISE EXCEPTION 'max_uses must be between 1 and 10';
    END IF;

    IF char_length(trim(COALESCE(p_invite_code_hash, ''))) < 32 THEN
        RAISE EXCEPTION 'Valid invite code hash is required';
    END IF;

    IF p_expires_at <= v_now THEN
        RAISE EXCEPTION 'Invitation expiration must be in the future';
    END IF;

    IF p_target_person_id IS NOT NULL THEN
        IF EXISTS (
            SELECT 1 FROM public.family_members fm
            WHERE fm.family_id = p_family_id
              AND fm.linked_person_id = p_target_person_id
              AND fm.status = 'ACTIVE'
        ) THEN
            RAISE EXCEPTION 'Target person record is already linked to another active account';
        END IF;

        IF EXISTS (
            SELECT 1 FROM public.persons p
            WHERE p.id = p_target_person_id AND p.family_id = p_family_id AND p.is_deleted = FALSE
        ) THEN
            v_resolved_target := p_target_person_id;
        ELSE
            v_pending_target := p_target_person_id;
        END IF;
    END IF;

    IF p_branch_root_person_id IS NOT NULL THEN
        IF EXISTS (
            SELECT 1 FROM public.persons p
            WHERE p.id = p_branch_root_person_id AND p.family_id = p_family_id AND p.is_deleted = FALSE
        ) THEN
            v_resolved_branch := p_branch_root_person_id;
        ELSE
            v_pending_branch := p_branch_root_person_id;
        END IF;
    END IF;

    INSERT INTO public.family_invitations (
        family_id,
        target_person_id,
        pending_target_person_id,
        assigned_role,
        branch_root_person_id,
        pending_branch_root_person_id,
        invite_code_hash,
        invite_code_hint,
        max_uses,
        uses_count,
        expires_at,
        status,
        created_by_user_id,
        created_at,
        updated_at
    ) VALUES (
        p_family_id,
        v_resolved_target,
        v_pending_target,
        p_assigned_role,
        v_resolved_branch,
        v_pending_branch,
        trim(p_invite_code_hash),
        trim(p_invite_code_hint),
        p_max_uses,
        0,
        p_expires_at,
        'PENDING',
        v_uid,
        v_now,
        v_now
    )
    RETURNING id INTO v_inv_id;

    INSERT INTO public.audit_logs (
        family_id, actor_user_id, actor_member_role, entity_type, entity_id, operation, old_value, new_value, operation_date
    ) VALUES (
        p_family_id, v_uid, v_caller.role, 'FamilyInvitations', v_inv_id::TEXT, 'CREATE', NULL,
        'role=' || p_assigned_role || ', hint=' || trim(p_invite_code_hint), v_now
    );

    RETURN jsonb_build_object(
        'id', v_inv_id,
        'family_id', p_family_id,
        'target_person_id', COALESCE(v_resolved_target, v_pending_target),
        'assigned_role', p_assigned_role,
        'branch_root_person_id', COALESCE(v_resolved_branch, v_pending_branch, v_resolved_target, v_pending_target),
        'invite_code_hash', trim(p_invite_code_hash),
        'invite_code_hint', trim(p_invite_code_hint),
        'max_uses', p_max_uses,
        'uses_count', 0,
        'expires_at', p_expires_at,
        'status', 'PENDING',
        'created_by_user_id', v_uid,
        'accepted_by_user_id', NULL,
        'created_at', v_now,
        'updated_at', v_now
    );
END;
$$;

-- Enhanced accept_family_invitation: auto-ensures user_profiles, normalizes case, returns full workspace summary
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
    v_family public.families;
    v_member public.family_members;
    v_user_email TEXT;
    v_display_name TEXT;
    v_effective_target_id UUID;
    v_effective_branch_id UUID;
    v_fk_target_id UUID;
    v_fk_branch_id UUID;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required to accept invitation';
    END IF;

    v_code_hash := encode(digest(upper(trim(COALESCE(p_invite_code, ''))), 'sha256'), 'hex');

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

    SELECT * INTO v_family FROM public.families WHERE id = v_inv.family_id AND is_deleted = FALSE;
    IF v_family.id IS NULL THEN
        RAISE EXCEPTION 'Family workspace no longer exists';
    END IF;

    -- Ensure user_profiles row exists for auth.uid()
    SELECT u.email, COALESCE(u.raw_user_meta_data ->> 'display_name', split_part(u.email, '@', 1))
    INTO v_user_email, v_display_name
    FROM auth.users u
    WHERE u.id = v_uid;

    INSERT INTO public.user_profiles (id, email, display_name, created_at, updated_at)
    VALUES (
        v_uid,
        COALESCE(v_user_email, v_uid::TEXT || '@juthoor.local'),
        COALESCE(NULLIF(trim(v_display_name), ''), 'مستخدم جذور'),
        v_now,
        v_now
    )
    ON CONFLICT (id) DO NOTHING;

    v_effective_target_id := COALESCE(v_inv.target_person_id, v_inv.pending_target_person_id);
    v_effective_branch_id := COALESCE(v_inv.branch_root_person_id, v_inv.pending_branch_root_person_id, v_effective_target_id);

    IF v_effective_target_id IS NOT NULL AND EXISTS (
        SELECT 1 FROM public.family_members fm
        WHERE fm.family_id = v_inv.family_id
          AND fm.linked_person_id = v_effective_target_id
          AND fm.status = 'ACTIVE'
          AND fm.user_id <> v_uid
    ) THEN
        RAISE EXCEPTION 'Target person record is already linked to another active account';
    END IF;

    -- Only bind FK column if target person has already synced to public.persons
    SELECT id INTO v_fk_target_id FROM public.persons WHERE id = v_effective_target_id AND family_id = v_inv.family_id;
    SELECT id INTO v_fk_branch_id FROM public.persons WHERE id = v_effective_branch_id AND family_id = v_inv.family_id;

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
        v_fk_target_id,
        v_inv.assigned_role,
        COALESCE(v_fk_branch_id, v_fk_target_id),
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
        role = CASE WHEN family_members.role = 'OWNER' THEN 'OWNER' ELSE EXCLUDED.role END,
        branch_root_person_id = COALESCE(EXCLUDED.branch_root_person_id, family_members.branch_root_person_id),
        can_invite = EXCLUDED.can_invite,
        can_export = EXCLUDED.can_export,
        can_manage_media = EXCLUDED.can_manage_media,
        status = 'ACTIVE',
        updated_at = v_now,
        version = family_members.version + 1
    RETURNING * INTO v_member;

    UPDATE public.family_invitations
    SET uses_count = uses_count + 1,
        accepted_by_user_id = v_uid,
        status = CASE WHEN uses_count + 1 >= max_uses THEN 'ACCEPTED' ELSE 'PENDING' END,
        updated_at = v_now
    WHERE id = v_inv.id;

    INSERT INTO public.audit_logs (
        family_id, actor_user_id, actor_member_role, entity_type, entity_id, operation, old_value, new_value, operation_date
    ) VALUES (
        v_inv.family_id, v_uid, v_member.role, 'FamilyMembers', v_member.id::TEXT, 'CREATE', NULL,
        'Accepted invitation linked_person=' || COALESCE(v_effective_target_id::TEXT, 'NONE'), v_now
    );

    RETURN jsonb_build_object(
        'family_id', v_family.id,
        'family_name', v_family.name,
        'family_motto', v_family.motto,
        'owner_user_id', v_family.owner_user_id,
        'root_person_id', v_family.root_person_id,
        'family_version', v_family.version,
        'family_created_at', v_family.created_at,
        'family_updated_at', v_family.updated_at,
        'member_id', v_member.id,
        'linked_person_id', COALESCE(v_member.linked_person_id, v_effective_target_id),
        'role', v_member.role,
        'branch_root_person_id', COALESCE(v_member.branch_root_person_id, v_effective_branch_id),
        'can_invite', v_member.can_invite,
        'can_export', v_member.can_export,
        'can_manage_media', v_member.can_manage_media,
        'status', v_member.status,
        'joined_at', v_member.joined_at,
        'member_updated_at', v_member.updated_at,
        'member_version', v_member.version
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.revoke_family_invitation(p_invitation_id UUID)
RETURNS VOID
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_now BIGINT := (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT;
    v_inv public.family_invitations;
    v_caller public.family_members;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT * INTO v_inv FROM public.family_invitations WHERE id = p_invitation_id FOR UPDATE;
    IF v_inv.id IS NULL THEN
        RAISE EXCEPTION 'Invitation not found';
    END IF;

    SELECT * INTO v_caller FROM public.get_active_member(v_inv.family_id);
    IF v_caller.id IS NULL OR (v_caller.role NOT IN ('OWNER', 'ADMIN') AND v_inv.created_by_user_id <> v_uid) THEN
        RAISE EXCEPTION 'Not authorized to revoke invitation';
    END IF;

    UPDATE public.family_invitations
    SET status = 'REVOKED', updated_at = v_now
    WHERE id = p_invitation_id;

    INSERT INTO public.audit_logs (
        family_id, actor_user_id, actor_member_role, entity_type, entity_id, operation, old_value, new_value, operation_date
    ) VALUES (
        v_inv.family_id, v_uid, v_caller.role, 'FamilyInvitations', p_invitation_id::TEXT, 'UPDATE', v_inv.status, 'REVOKED', v_now
    );
END;
$$;

CREATE OR REPLACE FUNCTION public.get_family_invitations(p_family_id UUID)
RETURNS TABLE (
    id UUID,
    family_id UUID,
    target_person_id UUID,
    assigned_role TEXT,
    branch_root_person_id UUID,
    invite_code_hash TEXT,
    invite_code_hint TEXT,
    max_uses INT,
    uses_count INT,
    expires_at BIGINT,
    status TEXT,
    created_by_user_id UUID,
    accepted_by_user_id UUID,
    created_at BIGINT,
    updated_at BIGINT
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_caller public.family_members;
BEGIN
    SELECT * INTO v_caller FROM public.get_active_member(p_family_id);
    IF v_caller.id IS NULL OR (v_caller.role NOT IN ('OWNER', 'ADMIN') AND NOT v_caller.can_invite) THEN
        RAISE EXCEPTION 'Not authorized to view invitations';
    END IF;

    RETURN QUERY
    SELECT
        fi.id,
        fi.family_id,
        COALESCE(fi.target_person_id, fi.pending_target_person_id) AS target_person_id,
        fi.assigned_role,
        COALESCE(fi.branch_root_person_id, fi.pending_branch_root_person_id) AS branch_root_person_id,
        fi.invite_code_hash,
        fi.invite_code_hint,
        fi.max_uses,
        fi.uses_count,
        fi.expires_at,
        fi.status,
        fi.created_by_user_id,
        fi.accepted_by_user_id,
        fi.created_at,
        fi.updated_at
    FROM public.family_invitations fi
    WHERE fi.family_id = p_family_id
    ORDER BY fi.created_at DESC;
END;
$$;

CREATE OR REPLACE FUNCTION public.get_family_members_with_profiles(p_family_id UUID)
RETURNS TABLE (
    member_id UUID,
    family_id UUID,
    user_id UUID,
    email TEXT,
    display_name TEXT,
    linked_person_id UUID,
    role TEXT,
    branch_root_person_id UUID,
    can_invite BOOLEAN,
    can_export BOOLEAN,
    can_manage_media BOOLEAN,
    status TEXT,
    invited_by_user_id UUID,
    joined_at BIGINT,
    updated_at BIGINT,
    version BIGINT
)
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
BEGIN
    IF NOT public.is_active_family_member(p_family_id) THEN
        RAISE EXCEPTION 'Not authorized to view family members';
    END IF;

    RETURN QUERY
    SELECT
        fm.id AS member_id,
        fm.family_id,
        fm.user_id,
        up.email,
        up.display_name,
        fm.linked_person_id,
        fm.role,
        fm.branch_root_person_id,
        fm.can_invite,
        fm.can_export,
        fm.can_manage_media,
        fm.status,
        fm.invited_by_user_id,
        fm.joined_at,
        fm.updated_at,
        fm.version
    FROM public.family_members fm
    JOIN public.user_profiles up ON up.id = fm.user_id
    WHERE fm.family_id = p_family_id
    ORDER BY fm.joined_at ASC;
END;
$$;

CREATE OR REPLACE FUNCTION public.update_family_member_link_and_role(
    p_family_id UUID,
    p_member_id UUID,
    p_linked_person_id UUID DEFAULT NULL,
    p_branch_root_person_id UUID DEFAULT NULL,
    p_role TEXT DEFAULT NULL,
    p_can_invite BOOLEAN DEFAULT NULL,
    p_can_export BOOLEAN DEFAULT NULL,
    p_can_manage_media BOOLEAN DEFAULT NULL,
    p_status TEXT DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_now BIGINT := (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT;
    v_caller public.family_members;
    v_target public.family_members;
    v_profile public.user_profiles;
    v_next_role TEXT;
    v_next_status TEXT;
    v_fk_linked UUID;
    v_fk_branch UUID;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT * INTO v_caller FROM public.get_active_member(p_family_id);
    IF v_caller.id IS NULL OR v_caller.role NOT IN ('OWNER', 'ADMIN') THEN
        RAISE EXCEPTION 'Not authorized to manage family members';
    END IF;

    SELECT * INTO v_target FROM public.family_members WHERE id = p_member_id AND family_id = p_family_id FOR UPDATE;
    IF v_target.id IS NULL THEN
        RAISE EXCEPTION 'Family member not found';
    END IF;

    v_next_role := COALESCE(p_role, v_target.role);
    v_next_status := COALESCE(p_status, v_target.status);

    IF v_target.role = 'OWNER' AND (v_next_role <> 'OWNER' OR v_next_status <> 'ACTIVE') THEN
        RAISE EXCEPTION 'Family owner role and active status cannot be revoked';
    END IF;

    IF v_caller.role = 'ADMIN' THEN
        IF v_target.role IN ('OWNER', 'ADMIN') AND v_target.user_id <> v_uid THEN
            RAISE EXCEPTION 'Admin cannot modify owner or another admin';
        END IF;
        IF v_next_role IN ('OWNER', 'ADMIN') AND v_next_role <> v_target.role THEN
            RAISE EXCEPTION 'Only family owner can assign ADMIN role';
        END IF;
    END IF;

    IF v_next_role = 'OWNER' AND v_target.role <> 'OWNER' THEN
        RAISE EXCEPTION 'Cannot assign OWNER role to another member';
    END IF;

    IF p_linked_person_id IS NOT NULL AND v_next_status = 'ACTIVE' THEN
        IF EXISTS (
            SELECT 1 FROM public.family_members fm
            WHERE fm.family_id = p_family_id
              AND fm.linked_person_id = p_linked_person_id
              AND fm.status = 'ACTIVE'
              AND fm.id <> p_member_id
        ) THEN
            RAISE EXCEPTION 'Target person record is already linked to another active account';
        END IF;
        SELECT id INTO v_fk_linked FROM public.persons WHERE id = p_linked_person_id AND family_id = p_family_id;
    END IF;

    IF p_branch_root_person_id IS NOT NULL THEN
        SELECT id INTO v_fk_branch FROM public.persons WHERE id = p_branch_root_person_id AND family_id = p_family_id;
    END IF;

    UPDATE public.family_members
    SET linked_person_id = v_fk_linked,
        branch_root_person_id = COALESCE(v_fk_branch, v_fk_linked),
        role = v_next_role,
        can_invite = COALESCE(p_can_invite, can_invite),
        can_export = COALESCE(p_can_export, can_export),
        can_manage_media = COALESCE(p_can_manage_media, can_manage_media),
        status = v_next_status,
        updated_at = v_now,
        version = version + 1
    WHERE id = p_member_id
    RETURNING * INTO v_target;

    SELECT * INTO v_profile FROM public.user_profiles WHERE id = v_target.user_id;

    INSERT INTO public.audit_logs (
        family_id, actor_user_id, actor_member_role, entity_type, entity_id, operation, old_value, new_value, operation_date
    ) VALUES (
        p_family_id, v_uid, v_caller.role, 'FamilyMembers', p_member_id::TEXT, 'UPDATE', NULL,
        'role=' || v_target.role || ', status=' || v_target.status || ', linked=' || COALESCE(p_linked_person_id::TEXT, 'NONE'), v_now
    );

    RETURN jsonb_build_object(
        'member_id', v_target.id,
        'family_id', v_target.family_id,
        'user_id', v_target.user_id,
        'email', COALESCE(v_profile.email, ''),
        'display_name', COALESCE(v_profile.display_name, ''),
        'linked_person_id', COALESCE(v_target.linked_person_id, p_linked_person_id),
        'role', v_target.role,
        'branch_root_person_id', COALESCE(v_target.branch_root_person_id, p_branch_root_person_id, p_linked_person_id),
        'can_invite', v_target.can_invite,
        'can_export', v_target.can_export,
        'can_manage_media', v_target.can_manage_media,
        'status', v_target.status,
        'invited_by_user_id', v_target.invited_by_user_id,
        'joined_at', v_target.joined_at,
        'updated_at', v_target.updated_at,
        'version', v_target.version
    );
END;
$$;
