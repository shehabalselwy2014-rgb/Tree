-- ============================================================================
-- Juthoor (جِذور) Family Space Creation & Membership Lookup RPCs
-- Migration 0003
-- ============================================================================

-- Atomically creates a family workspace and assigns the authenticated founder
-- as OWNER in family_members, avoiding circular RLS chicken-and-egg blocks.
CREATE OR REPLACE FUNCTION public.create_family_space(
    p_family_id UUID DEFAULT NULL,
    p_name TEXT DEFAULT '',
    p_motto TEXT DEFAULT 'نوثّق الأصول، ونحفظ امتداد الأجيال',
    p_root_person_id UUID DEFAULT NULL
)
RETURNS JSONB
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_now BIGINT := (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT;
    v_family_id UUID := COALESCE(p_family_id, gen_random_uuid());
    v_member_id UUID;
    v_clean_name TEXT := trim(COALESCE(p_name, ''));
    v_clean_motto TEXT := COALESCE(NULLIF(trim(p_motto), ''), 'نوثّق الأصول، ونحفظ امتداد الأجيال');
    v_user_email TEXT;
    v_display_name TEXT;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    IF char_length(v_clean_name) = 0 THEN
        RAISE EXCEPTION 'Family name cannot be blank';
    END IF;

    -- Ensure user_profiles row exists for auth.uid() before FK insertion
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

    INSERT INTO public.families (
        id,
        name,
        motto,
        owner_user_id,
        root_person_id,
        version,
        is_deleted,
        created_at,
        updated_at
    ) VALUES (
        v_family_id,
        v_clean_name,
        v_clean_motto,
        v_uid,
        p_root_person_id,
        1,
        FALSE,
        v_now,
        v_now
    );

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
        v_family_id,
        v_uid,
        p_root_person_id,
        'OWNER',
        p_root_person_id,
        TRUE,
        TRUE,
        TRUE,
        'ACTIVE',
        v_uid,
        v_now,
        v_now,
        1
    )
    RETURNING id INTO v_member_id;

    INSERT INTO public.audit_logs (
        family_id,
        actor_user_id,
        actor_member_role,
        entity_type,
        entity_id,
        operation,
        old_value,
        new_value,
        operation_date
    ) VALUES (
        v_family_id,
        v_uid,
        'OWNER',
        'Families',
        v_family_id::TEXT,
        'CREATE',
        NULL,
        v_clean_name,
        v_now
    );

    RETURN jsonb_build_object(
        'family_id', v_family_id,
        'name', v_clean_name,
        'motto', v_clean_motto,
        'owner_user_id', v_uid,
        'root_person_id', p_root_person_id,
        'member_id', v_member_id,
        'role', 'OWNER',
        'created_at', v_now,
        'updated_at', v_now
    );
END;
$$;

-- Returns all active family workspaces and memberships for auth.uid()
CREATE OR REPLACE FUNCTION public.get_my_family_memberships()
RETURNS TABLE (
    family_id UUID,
    family_name TEXT,
    family_motto TEXT,
    owner_user_id UUID,
    root_person_id UUID,
    family_version BIGINT,
    family_created_at BIGINT,
    family_updated_at BIGINT,
    member_id UUID,
    linked_person_id UUID,
    role TEXT,
    branch_root_person_id UUID,
    can_invite BOOLEAN,
    can_export BOOLEAN,
    can_manage_media BOOLEAN,
    status TEXT,
    joined_at BIGINT,
    member_updated_at BIGINT,
    member_version BIGINT
)
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
    SELECT
        f.id AS family_id,
        f.name AS family_name,
        f.motto AS family_motto,
        f.owner_user_id,
        f.root_person_id,
        f.version AS family_version,
        f.created_at AS family_created_at,
        f.updated_at AS family_updated_at,
        fm.id AS member_id,
        fm.linked_person_id,
        fm.role,
        fm.branch_root_person_id,
        fm.can_invite,
        fm.can_export,
        fm.can_manage_media,
        fm.status,
        fm.joined_at,
        fm.updated_at AS member_updated_at,
        fm.version AS member_version
    FROM public.families f
    JOIN public.family_members fm ON fm.family_id = f.id
    WHERE fm.user_id = auth.uid()
      AND fm.status = 'ACTIVE'
      AND f.is_deleted = FALSE
    ORDER BY f.created_at ASC;
$$;
