-- ============================================================================
-- Juthoor (جِذور) Security Hardening Migration 0006
-- Enforces consistent RESTRICTED_MEMBER branch scoping across all relational
-- genealogy tables (marriages, parent_child_relationships, education, events,
-- photo_person_tags) and within sync_pull_family_delta RPC.
-- ============================================================================

-- Helper: returns TRUE if the active user is allowed to view p_person_id
-- within p_family_id. Non-restricted active members (OWNER, ADMIN,
-- BRANCH_MANAGER, MEMBER) can view all family members; RESTRICTED_MEMBER is
-- strictly limited to their authorized branch + branch spouses.
CREATE OR REPLACE FUNCTION public.can_view_person_in_family(
    p_family_id UUID,
    p_person_id UUID
)
RETURNS BOOLEAN
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_member public.family_members;
BEGIN
    IF p_person_id IS NULL THEN
        RETURN FALSE;
    END IF;

    SELECT * INTO v_member FROM public.get_active_member(p_family_id);
    IF v_member.id IS NULL THEN
        RETURN FALSE;
    END IF;

    IF v_member.role <> 'RESTRICTED_MEMBER' THEN
        RETURN TRUE;
    END IF;

    RETURN COALESCE(public.is_person_within_member_branch(p_family_id, p_person_id), FALSE);
END;
$$;

-- ============================================================================
-- Tighten SELECT RLS Policies for RESTRICTED_MEMBER Branch Scoping
-- ============================================================================

DROP POLICY IF EXISTS marriages_select_member ON public.marriages;
CREATE POLICY marriages_select_member ON public.marriages
    FOR SELECT USING (
        public.can_view_person_in_family(family_id, partner_one_id)
        AND public.can_view_person_in_family(family_id, partner_two_id)
    );

DROP POLICY IF EXISTS parent_child_select_member ON public.parent_child_relationships;
CREATE POLICY parent_child_select_member ON public.parent_child_relationships
    FOR SELECT USING (
        public.can_view_person_in_family(family_id, parent_id)
        AND public.can_view_person_in_family(family_id, child_id)
    );

DROP POLICY IF EXISTS education_select_member ON public.education;
CREATE POLICY education_select_member ON public.education
    FOR SELECT USING (
        public.can_view_person_in_family(family_id, person_id)
    );

DROP POLICY IF EXISTS events_select_member ON public.events;
CREATE POLICY events_select_member ON public.events
    FOR SELECT USING (
        EXISTS (
            SELECT 1 FROM public.family_members fm
            WHERE fm.family_id = events.family_id
              AND fm.user_id = auth.uid()
              AND fm.status = 'ACTIVE'
              AND (
                  fm.role <> 'RESTRICTED_MEMBER'
                  OR (
                      events.person_id IS NOT NULL
                      AND public.is_person_within_member_branch(events.family_id, events.person_id)
                  )
              )
        )
    );

DROP POLICY IF EXISTS photo_tags_select_member ON public.photo_person_tags;
CREATE POLICY photo_tags_select_member ON public.photo_person_tags
    FOR SELECT USING (
        public.can_view_person_in_family(family_id, person_id)
    );

-- ============================================================================
-- Update Incremental Delta Pull RPC to Enforce Identical Branch Scoping
-- ============================================================================
CREATE OR REPLACE FUNCTION public.sync_pull_family_delta(
    p_family_id UUID,
    p_since_updated_at BIGINT DEFAULT 0
)
RETURNS JSONB
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    v_uid UUID := auth.uid();
    v_now BIGINT := (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT;
    v_member public.family_members;
    v_since BIGINT := GREATEST(0, COALESCE(p_since_updated_at, 0));
    v_family JSONB;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT * INTO v_member FROM public.get_active_member(p_family_id);
    IF v_member.id IS NULL THEN
        RAISE EXCEPTION 'Not authorized: active family membership required';
    END IF;

    SELECT to_jsonb(f) INTO v_family
    FROM public.families f
    WHERE f.id = p_family_id AND f.updated_at > v_since;

    RETURN jsonb_build_object(
        'server_time', v_now,
        'family', v_family,
        'family_groups', COALESCE((
            SELECT jsonb_agg(to_jsonb(fg) ORDER BY fg.created_at ASC)
            FROM public.family_groups fg
            WHERE fg.family_id = p_family_id AND fg.updated_at > v_since
        ), '[]'::JSONB),
        'persons', COALESCE((
            SELECT jsonb_agg(to_jsonb(p) ORDER BY p.created_at ASC)
            FROM public.persons p
            WHERE p.family_id = p_family_id
              AND p.updated_at > v_since
              AND (
                  v_member.role <> 'RESTRICTED_MEMBER'
                  OR public.is_person_within_member_branch(p_family_id, p.id)
              )
        ), '[]'::JSONB),
        'marriages', COALESCE((
            SELECT jsonb_agg(to_jsonb(m) ORDER BY m.created_at ASC)
            FROM public.marriages m
            WHERE m.family_id = p_family_id
              AND m.updated_at > v_since
              AND (
                  v_member.role <> 'RESTRICTED_MEMBER'
                  OR (
                      public.is_person_within_member_branch(p_family_id, m.partner_one_id)
                      AND public.is_person_within_member_branch(p_family_id, m.partner_two_id)
                  )
              )
        ), '[]'::JSONB),
        'parent_child_relationships', COALESCE((
            SELECT jsonb_agg(to_jsonb(r) ORDER BY r.created_at ASC)
            FROM public.parent_child_relationships r
            WHERE r.family_id = p_family_id
              AND r.updated_at > v_since
              AND (
                  v_member.role <> 'RESTRICTED_MEMBER'
                  OR (
                      public.is_person_within_member_branch(p_family_id, r.parent_id)
                      AND public.is_person_within_member_branch(p_family_id, r.child_id)
                  )
              )
        ), '[]'::JSONB),
        'education', COALESCE((
            SELECT jsonb_agg(to_jsonb(e) ORDER BY e.created_at ASC)
            FROM public.education e
            WHERE e.family_id = p_family_id
              AND e.updated_at > v_since
              AND (
                  v_member.role <> 'RESTRICTED_MEMBER'
                  OR public.is_person_within_member_branch(p_family_id, e.person_id)
              )
        ), '[]'::JSONB),
        'person_photos', COALESCE((
            SELECT jsonb_agg(to_jsonb(ph) ORDER BY ph.created_at ASC)
            FROM public.person_photos ph
            WHERE ph.family_id = p_family_id
              AND ph.updated_at > v_since
              AND (
                  v_member.role <> 'RESTRICTED_MEMBER'
                  OR (ph.person_id IS NOT NULL AND public.is_person_within_member_branch(p_family_id, ph.person_id))
              )
        ), '[]'::JSONB),
        'photo_person_tags', COALESCE((
            SELECT jsonb_agg(to_jsonb(pt))
            FROM public.photo_person_tags pt
            WHERE pt.family_id = p_family_id
              AND pt.updated_at > v_since
              AND (
                  v_member.role <> 'RESTRICTED_MEMBER'
                  OR public.is_person_within_member_branch(p_family_id, pt.person_id)
              )
        ), '[]'::JSONB),
        'events', COALESCE((
            SELECT jsonb_agg(to_jsonb(ev) ORDER BY ev.created_at ASC)
            FROM public.events ev
            WHERE ev.family_id = p_family_id
              AND ev.updated_at > v_since
              AND (
                  v_member.role <> 'RESTRICTED_MEMBER'
                  OR (ev.person_id IS NOT NULL AND public.is_person_within_member_branch(p_family_id, ev.person_id))
              )
        ), '[]'::JSONB),
        'family_members', COALESCE((
            SELECT jsonb_agg(jsonb_build_object(
                'member_id', fm.id,
                'family_id', fm.family_id,
                'user_id', fm.user_id,
                'email', up.email,
                'display_name', up.display_name,
                'linked_person_id', fm.linked_person_id,
                'role', fm.role,
                'branch_root_person_id', fm.branch_root_person_id,
                'can_invite', fm.can_invite,
                'can_export', fm.can_export,
                'can_manage_media', fm.can_manage_media,
                'status', fm.status,
                'invited_by_user_id', fm.invited_by_user_id,
                'joined_at', fm.joined_at,
                'updated_at', fm.updated_at,
                'version', fm.version
            ) ORDER BY fm.joined_at ASC)
            FROM public.family_members fm
            JOIN public.user_profiles up ON up.id = fm.user_id
            WHERE fm.family_id = p_family_id AND fm.updated_at > v_since
        ), '[]'::JSONB)
    );
END;
$$;
