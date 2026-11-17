-- ============================================================================
-- Juthoor (جِذور) Incremental Bi-Directional Sync Engine & Conflict RPCs
-- Migration 0005
-- ============================================================================

CREATE OR REPLACE FUNCTION public.sync_push_genealogy_mutation(
    p_family_id UUID,
    p_entity_type TEXT,
    p_entity_id UUID,
    p_operation TEXT,
    p_client_version BIGINT,
    p_force_overwrite BOOLEAN DEFAULT FALSE,
    p_payload JSONB DEFAULT '{}'::JSONB
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
    v_existing_version BIGINT;
    v_existing_updated_by UUID;
    v_server_json JSONB;
    v_new_version BIGINT;
    v_is_delete BOOLEAN := (upper(trim(COALESCE(p_operation, 'UPSERT'))) = 'SOFT_DELETE');
    v_deleted_at BIGINT;
    v_p1 UUID;
    v_p2 UUID;
    v_person_id UUID;
    v_family_group_id UUID;
    v_root_person_id UUID;
    v_marriage_id UUID;
    v_tag_id TEXT;
BEGIN
    IF v_uid IS NULL THEN
        RAISE EXCEPTION 'Authentication required';
    END IF;

    SELECT * INTO v_caller FROM public.get_active_member(p_family_id);
    IF v_caller.id IS NULL THEN
        RAISE EXCEPTION 'Not authorized: active family membership required';
    END IF;

    IF v_caller.role IN ('MEMBER', 'RESTRICTED_MEMBER') THEN
        RAISE EXCEPTION 'Not authorized to push edits';
    END IF;

    v_deleted_at := CASE
        WHEN v_is_delete THEN COALESCE((p_payload ->> 'deleted_at')::BIGINT, v_now)
        ELSE NULL
    END;

    CASE upper(trim(p_entity_type))
        WHEN 'FAMILY' THEN
            IF v_caller.role NOT IN ('OWNER', 'ADMIN') THEN
                RAISE EXCEPTION 'Not authorized to modify family metadata';
            END IF;

            SELECT f.version, f.owner_user_id, to_jsonb(f)
            INTO v_existing_version, v_existing_updated_by, v_server_json
            FROM public.families f WHERE f.id = p_family_id FOR UPDATE;

            IF v_existing_version IS NOT NULL AND NOT p_force_overwrite AND (
                v_existing_version > p_client_version
            ) THEN
                RETURN jsonb_build_object(
                    'status', 'CONFLICT',
                    'entity_type', 'FAMILY',
                    'entity_id', p_entity_id,
                    'server_version', v_existing_version,
                    'server_record', v_server_json
                );
            END IF;

            v_root_person_id := NULLIF(p_payload ->> 'root_person_id', '')::UUID;
            IF v_root_person_id IS NOT NULL AND NOT EXISTS (
                SELECT 1 FROM public.persons p WHERE p.id = v_root_person_id AND p.family_id = p_family_id
            ) THEN
                v_root_person_id := NULL;
            END IF;

            v_new_version := CASE
                WHEN v_existing_version IS NULL THEN GREATEST(1, p_client_version)
                WHEN p_force_overwrite THEN v_existing_version + 1
                ELSE GREATEST(v_existing_version + 1, p_client_version)
            END;

            UPDATE public.families
            SET name = COALESCE(NULLIF(trim(p_payload ->> 'name'), ''), name),
                motto = COALESCE(NULLIF(trim(p_payload ->> 'motto'), ''), motto),
                root_person_id = COALESCE(v_root_person_id, root_person_id),
                version = v_new_version,
                is_deleted = v_is_delete,
                deleted_at = v_deleted_at,
                updated_at = v_now
            WHERE id = p_family_id;

        WHEN 'FAMILY_GROUP' THEN
            IF v_caller.role NOT IN ('OWNER', 'ADMIN') THEN
                RAISE EXCEPTION 'Not authorized to manage family groups';
            END IF;

            SELECT fg.version, fg.updated_by_user_id, to_jsonb(fg)
            INTO v_existing_version, v_existing_updated_by, v_server_json
            FROM public.family_groups fg WHERE fg.id = p_entity_id AND fg.family_id = p_family_id FOR UPDATE;

            IF v_existing_version IS NOT NULL AND NOT p_force_overwrite AND (
                v_existing_version > p_client_version OR (v_existing_version = p_client_version AND v_existing_updated_by IS DISTINCT FROM v_uid)
            ) THEN
                RETURN jsonb_build_object(
                    'status', 'CONFLICT',
                    'entity_type', 'FAMILY_GROUP',
                    'entity_id', p_entity_id,
                    'server_version', v_existing_version,
                    'server_record', v_server_json
                );
            END IF;

            v_root_person_id := NULLIF(p_payload ->> 'root_person_id', '')::UUID;
            IF v_root_person_id IS NOT NULL AND NOT EXISTS (
                SELECT 1 FROM public.persons p WHERE p.id = v_root_person_id AND p.family_id = p_family_id
            ) THEN
                v_root_person_id := NULL;
            END IF;

            v_new_version := CASE
                WHEN v_existing_version IS NULL THEN GREATEST(1, p_client_version)
                WHEN p_force_overwrite THEN v_existing_version + 1
                ELSE GREATEST(v_existing_version + 1, p_client_version)
            END;

            INSERT INTO public.family_groups (
                id, family_id, group_name, description, root_person_id,
                created_by_user_id, updated_by_user_id, version, is_deleted, deleted_at, created_at, updated_at
            ) VALUES (
                p_entity_id,
                p_family_id,
                COALESCE(NULLIF(trim(p_payload ->> 'group_name'), ''), 'فرع عائلي'),
                p_payload ->> 'description',
                v_root_person_id,
                v_uid,
                v_uid,
                v_new_version,
                v_is_delete,
                v_deleted_at,
                COALESCE((p_payload ->> 'created_at')::BIGINT, v_now),
                v_now
            )
            ON CONFLICT (id) DO UPDATE SET
                group_name = EXCLUDED.group_name,
                description = EXCLUDED.description,
                root_person_id = EXCLUDED.root_person_id,
                updated_by_user_id = v_uid,
                version = v_new_version,
                is_deleted = v_is_delete,
                deleted_at = v_deleted_at,
                updated_at = v_now;

        WHEN 'PERSON' THEN
            SELECT p.version, p.updated_by_user_id, to_jsonb(p)
            INTO v_existing_version, v_existing_updated_by, v_server_json
            FROM public.persons p WHERE p.id = p_entity_id AND p.family_id = p_family_id FOR UPDATE;

            IF v_existing_version IS NOT NULL THEN
                IF NOT (
                    public.can_edit_person_in_family(p_family_id, p_entity_id)
                    OR (v_caller.role = 'BRANCH_MANAGER' AND v_existing_updated_by = v_uid)
                ) THEN
                    RAISE EXCEPTION 'Not authorized to edit this person outside your branch';
                END IF;
            END IF;

            IF v_existing_version IS NOT NULL AND NOT p_force_overwrite AND (
                v_existing_version > p_client_version OR (v_existing_version = p_client_version AND v_existing_updated_by IS DISTINCT FROM v_uid)
            ) THEN
                RETURN jsonb_build_object(
                    'status', 'CONFLICT',
                    'entity_type', 'PERSON',
                    'entity_id', p_entity_id,
                    'server_version', v_existing_version,
                    'server_record', v_server_json
                );
            END IF;

            v_family_group_id := NULLIF(p_payload ->> 'family_group_id', '')::UUID;
            IF v_family_group_id IS NOT NULL AND NOT EXISTS (
                SELECT 1 FROM public.family_groups fg WHERE fg.id = v_family_group_id AND fg.family_id = p_family_id
            ) THEN
                v_family_group_id := NULL;
            END IF;

            v_new_version := CASE
                WHEN v_existing_version IS NULL THEN GREATEST(1, p_client_version)
                WHEN p_force_overwrite THEN v_existing_version + 1
                ELSE GREATEST(v_existing_version + 1, p_client_version)
            END;

            INSERT INTO public.persons (
                id, family_id, full_name, first_name, middle_name, family_name, gender,
                birth_date, birth_date_precision, birth_place,
                death_date, death_date_precision, death_place,
                biography, occupation, current_marital_status,
                remote_avatar_key, avatar_sha256, family_group_id, notes,
                created_by_user_id, updated_by_user_id, version, is_deleted, deleted_at, created_at, updated_at
            ) VALUES (
                p_entity_id,
                p_family_id,
                COALESCE(NULLIF(trim(p_payload ->> 'full_name'), ''), 'فرد'),
                COALESCE(NULLIF(trim(p_payload ->> 'first_name'), ''), 'فرد'),
                p_payload ->> 'middle_name',
                p_payload ->> 'family_name',
                COALESCE(NULLIF(p_payload ->> 'gender', ''), 'MALE'),
                p_payload ->> 'birth_date',
                COALESCE(NULLIF(p_payload ->> 'birth_date_precision', ''), 'UNKNOWN'),
                p_payload ->> 'birth_place',
                p_payload ->> 'death_date',
                COALESCE(NULLIF(p_payload ->> 'death_date_precision', ''), 'UNKNOWN'),
                p_payload ->> 'death_place',
                p_payload ->> 'biography',
                p_payload ->> 'occupation',
                COALESCE(NULLIF(p_payload ->> 'current_marital_status', ''), 'SINGLE'),
                p_payload ->> 'remote_avatar_key',
                p_payload ->> 'avatar_sha256',
                v_family_group_id,
                p_payload ->> 'notes',
                v_uid,
                v_uid,
                v_new_version,
                v_is_delete,
                v_deleted_at,
                COALESCE((p_payload ->> 'created_at')::BIGINT, v_now),
                v_now
            )
            ON CONFLICT (id) DO UPDATE SET
                full_name = EXCLUDED.full_name,
                first_name = EXCLUDED.first_name,
                middle_name = EXCLUDED.middle_name,
                family_name = EXCLUDED.family_name,
                gender = EXCLUDED.gender,
                birth_date = EXCLUDED.birth_date,
                birth_date_precision = EXCLUDED.birth_date_precision,
                birth_place = EXCLUDED.birth_place,
                death_date = EXCLUDED.death_date,
                death_date_precision = EXCLUDED.death_date_precision,
                death_place = EXCLUDED.death_place,
                biography = EXCLUDED.biography,
                occupation = EXCLUDED.occupation,
                current_marital_status = EXCLUDED.current_marital_status,
                remote_avatar_key = COALESCE(EXCLUDED.remote_avatar_key, public.persons.remote_avatar_key),
                avatar_sha256 = COALESCE(EXCLUDED.avatar_sha256, public.persons.avatar_sha256),
                family_group_id = EXCLUDED.family_group_id,
                notes = EXCLUDED.notes,
                updated_by_user_id = v_uid,
                version = v_new_version,
                is_deleted = v_is_delete,
                deleted_at = v_deleted_at,
                updated_at = v_now;

        WHEN 'MARRIAGE' THEN
            v_p1 := (p_payload ->> 'partner_one_id')::UUID;
            v_p2 := (p_payload ->> 'partner_two_id')::UUID;

            IF NOT (
                v_caller.role IN ('OWNER', 'ADMIN')
                OR public.can_edit_person_in_family(p_family_id, v_p1)
                OR public.can_edit_person_in_family(p_family_id, v_p2)
            ) THEN
                RAISE EXCEPTION 'Not authorized to modify this marriage outside your branch';
            END IF;

            SELECT m.version, m.updated_by_user_id, to_jsonb(m)
            INTO v_existing_version, v_existing_updated_by, v_server_json
            FROM public.marriages m WHERE m.id = p_entity_id AND m.family_id = p_family_id FOR UPDATE;

            IF v_existing_version IS NOT NULL AND NOT p_force_overwrite AND (
                v_existing_version > p_client_version OR (v_existing_version = p_client_version AND v_existing_updated_by IS DISTINCT FROM v_uid)
            ) THEN
                RETURN jsonb_build_object(
                    'status', 'CONFLICT',
                    'entity_type', 'MARRIAGE',
                    'entity_id', p_entity_id,
                    'server_version', v_existing_version,
                    'server_record', v_server_json
                );
            END IF;

            v_new_version := CASE
                WHEN v_existing_version IS NULL THEN GREATEST(1, p_client_version)
                WHEN p_force_overwrite THEN v_existing_version + 1
                ELSE GREATEST(v_existing_version + 1, p_client_version)
            END;

            INSERT INTO public.marriages (
                id, family_id, partner_one_id, partner_two_id,
                marriage_date, marriage_date_precision, marriage_place,
                marriage_status, end_date, end_reason, notes,
                created_by_user_id, updated_by_user_id, version, is_deleted, deleted_at, created_at, updated_at
            ) VALUES (
                p_entity_id,
                p_family_id,
                v_p1,
                v_p2,
                p_payload ->> 'marriage_date',
                COALESCE(NULLIF(p_payload ->> 'marriage_date_precision', ''), 'UNKNOWN'),
                p_payload ->> 'marriage_place',
                COALESCE(NULLIF(p_payload ->> 'marriage_status', ''), 'ACTIVE'),
                p_payload ->> 'end_date',
                p_payload ->> 'end_reason',
                p_payload ->> 'notes',
                v_uid,
                v_uid,
                v_new_version,
                v_is_delete,
                v_deleted_at,
                COALESCE((p_payload ->> 'created_at')::BIGINT, v_now),
                v_now
            )
            ON CONFLICT (id) DO UPDATE SET
                partner_one_id = EXCLUDED.partner_one_id,
                partner_two_id = EXCLUDED.partner_two_id,
                marriage_date = EXCLUDED.marriage_date,
                marriage_date_precision = EXCLUDED.marriage_date_precision,
                marriage_place = EXCLUDED.marriage_place,
                marriage_status = EXCLUDED.marriage_status,
                end_date = EXCLUDED.end_date,
                end_reason = EXCLUDED.end_reason,
                notes = EXCLUDED.notes,
                updated_by_user_id = v_uid,
                version = v_new_version,
                is_deleted = v_is_delete,
                deleted_at = v_deleted_at,
                updated_at = v_now;

        WHEN 'PARENT_CHILD' THEN
            v_p1 := (p_payload ->> 'parent_id')::UUID;
            v_p2 := (p_payload ->> 'child_id')::UUID;
            v_marriage_id := NULLIF(p_payload ->> 'marriage_id', '')::UUID;

            IF NOT (
                v_caller.role IN ('OWNER', 'ADMIN')
                OR public.can_edit_person_in_family(p_family_id, v_p1)
                OR public.can_edit_person_in_family(p_family_id, v_p2)
            ) THEN
                RAISE EXCEPTION 'Not authorized to modify this parent-child link outside your branch';
            END IF;

            SELECT r.version, r.updated_by_user_id, to_jsonb(r)
            INTO v_existing_version, v_existing_updated_by, v_server_json
            FROM public.parent_child_relationships r WHERE r.id = p_entity_id AND r.family_id = p_family_id FOR UPDATE;

            IF v_existing_version IS NOT NULL AND NOT p_force_overwrite AND (
                v_existing_version > p_client_version OR (v_existing_version = p_client_version AND v_existing_updated_by IS DISTINCT FROM v_uid)
            ) THEN
                RETURN jsonb_build_object(
                    'status', 'CONFLICT',
                    'entity_type', 'PARENT_CHILD',
                    'entity_id', p_entity_id,
                    'server_version', v_existing_version,
                    'server_record', v_server_json
                );
            END IF;

            IF v_marriage_id IS NOT NULL AND NOT EXISTS (
                SELECT 1 FROM public.marriages m WHERE m.id = v_marriage_id AND m.family_id = p_family_id
            ) THEN
                v_marriage_id := NULL;
            END IF;

            v_new_version := CASE
                WHEN v_existing_version IS NULL THEN GREATEST(1, p_client_version)
                WHEN p_force_overwrite THEN v_existing_version + 1
                ELSE GREATEST(v_existing_version + 1, p_client_version)
            END;

            INSERT INTO public.parent_child_relationships (
                id, family_id, parent_id, child_id, relationship_type, marriage_id, notes,
                created_by_user_id, updated_by_user_id, version, is_deleted, deleted_at, created_at, updated_at
            ) VALUES (
                p_entity_id,
                p_family_id,
                v_p1,
                v_p2,
                COALESCE(NULLIF(p_payload ->> 'relationship_type', ''), 'BIOLOGICAL_FATHER'),
                v_marriage_id,
                p_payload ->> 'notes',
                v_uid,
                v_uid,
                v_new_version,
                v_is_delete,
                v_deleted_at,
                COALESCE((p_payload ->> 'created_at')::BIGINT, v_now),
                v_now
            )
            ON CONFLICT (id) DO UPDATE SET
                parent_id = EXCLUDED.parent_id,
                child_id = EXCLUDED.child_id,
                relationship_type = EXCLUDED.relationship_type,
                marriage_id = EXCLUDED.marriage_id,
                notes = EXCLUDED.notes,
                updated_by_user_id = v_uid,
                version = v_new_version,
                is_deleted = v_is_delete,
                deleted_at = v_deleted_at,
                updated_at = v_now;

        WHEN 'EDUCATION' THEN
            v_person_id := (p_payload ->> 'person_id')::UUID;
            IF NOT public.can_edit_person_in_family(p_family_id, v_person_id) THEN
                RAISE EXCEPTION 'Not authorized to modify education record outside your branch';
            END IF;

            SELECT e.version, e.updated_by_user_id, to_jsonb(e)
            INTO v_existing_version, v_existing_updated_by, v_server_json
            FROM public.education e WHERE e.id = p_entity_id AND e.family_id = p_family_id FOR UPDATE;

            IF v_existing_version IS NOT NULL AND NOT p_force_overwrite AND (
                v_existing_version > p_client_version OR (v_existing_version = p_client_version AND v_existing_updated_by IS DISTINCT FROM v_uid)
            ) THEN
                RETURN jsonb_build_object(
                    'status', 'CONFLICT',
                    'entity_type', 'EDUCATION',
                    'entity_id', p_entity_id,
                    'server_version', v_existing_version,
                    'server_record', v_server_json
                );
            END IF;

            v_new_version := CASE
                WHEN v_existing_version IS NULL THEN GREATEST(1, p_client_version)
                WHEN p_force_overwrite THEN v_existing_version + 1
                ELSE GREATEST(v_existing_version + 1, p_client_version)
            END;

            INSERT INTO public.education (
                id, family_id, person_id, qualification, specialization, institution, graduation_year, notes,
                created_by_user_id, updated_by_user_id, version, is_deleted, deleted_at, created_at, updated_at
            ) VALUES (
                p_entity_id,
                p_family_id,
                v_person_id,
                COALESCE(NULLIF(trim(p_payload ->> 'qualification'), ''), 'مؤهل'),
                p_payload ->> 'specialization',
                p_payload ->> 'institution',
                NULLIF(p_payload ->> 'graduation_year', '')::INT,
                p_payload ->> 'notes',
                v_uid,
                v_uid,
                v_new_version,
                v_is_delete,
                v_deleted_at,
                COALESCE((p_payload ->> 'created_at')::BIGINT, v_now),
                v_now
            )
            ON CONFLICT (id) DO UPDATE SET
                qualification = EXCLUDED.qualification,
                specialization = EXCLUDED.specialization,
                institution = EXCLUDED.institution,
                graduation_year = EXCLUDED.graduation_year,
                notes = EXCLUDED.notes,
                updated_by_user_id = v_uid,
                version = v_new_version,
                is_deleted = v_is_delete,
                deleted_at = v_deleted_at,
                updated_at = v_now;

        WHEN 'EVENT' THEN
            v_person_id := NULLIF(p_payload ->> 'person_id', '')::UUID;
            IF v_person_id IS NULL THEN
                IF v_caller.role NOT IN ('OWNER', 'ADMIN') THEN
                    RAISE EXCEPTION 'Not authorized to modify family-wide event';
                END IF;
            ELSIF NOT public.can_edit_person_in_family(p_family_id, v_person_id) THEN
                RAISE EXCEPTION 'Not authorized to modify event outside your branch';
            END IF;

            SELECT ev.version, ev.updated_by_user_id, to_jsonb(ev)
            INTO v_existing_version, v_existing_updated_by, v_server_json
            FROM public.events ev WHERE ev.id = p_entity_id AND ev.family_id = p_family_id FOR UPDATE;

            IF v_existing_version IS NOT NULL AND NOT p_force_overwrite AND (
                v_existing_version > p_client_version OR (v_existing_version = p_client_version AND v_existing_updated_by IS DISTINCT FROM v_uid)
            ) THEN
                RETURN jsonb_build_object(
                    'status', 'CONFLICT',
                    'entity_type', 'EVENT',
                    'entity_id', p_entity_id,
                    'server_version', v_existing_version,
                    'server_record', v_server_json
                );
            END IF;

            v_new_version := CASE
                WHEN v_existing_version IS NULL THEN GREATEST(1, p_client_version)
                WHEN p_force_overwrite THEN v_existing_version + 1
                ELSE GREATEST(v_existing_version + 1, p_client_version)
            END;

            INSERT INTO public.events (
                id, family_id, person_id, event_type, event_date, event_date_precision, event_place, description,
                created_by_user_id, updated_by_user_id, version, is_deleted, deleted_at, created_at, updated_at
            ) VALUES (
                p_entity_id,
                p_family_id,
                v_person_id,
                COALESCE(NULLIF(p_payload ->> 'event_type', ''), 'OTHER'),
                p_payload ->> 'event_date',
                COALESCE(NULLIF(p_payload ->> 'event_date_precision', ''), 'UNKNOWN'),
                p_payload ->> 'event_place',
                p_payload ->> 'description',
                v_uid,
                v_uid,
                v_new_version,
                v_is_delete,
                v_deleted_at,
                COALESCE((p_payload ->> 'created_at')::BIGINT, v_now),
                v_now
            )
            ON CONFLICT (id) DO UPDATE SET
                person_id = EXCLUDED.person_id,
                event_type = EXCLUDED.event_type,
                event_date = EXCLUDED.event_date,
                event_date_precision = EXCLUDED.event_date_precision,
                event_place = EXCLUDED.event_place,
                description = EXCLUDED.description,
                updated_by_user_id = v_uid,
                version = v_new_version,
                is_deleted = v_is_delete,
                deleted_at = v_deleted_at,
                updated_at = v_now;

        WHEN 'PHOTO' THEN
            v_person_id := NULLIF(p_payload ->> 'person_id', '')::UUID;
            IF NOT (
                v_caller.role IN ('OWNER', 'ADMIN')
                OR (v_person_id IS NOT NULL AND public.can_edit_person_in_family(p_family_id, v_person_id))
                OR v_caller.can_manage_media
            ) THEN
                RAISE EXCEPTION 'Not authorized to modify family photos';
            END IF;

            SELECT ph.version, ph.updated_by_user_id, to_jsonb(ph)
            INTO v_existing_version, v_existing_updated_by, v_server_json
            FROM public.person_photos ph WHERE ph.id = p_entity_id AND ph.family_id = p_family_id FOR UPDATE;

            IF v_existing_version IS NOT NULL AND NOT p_force_overwrite AND (
                v_existing_version > p_client_version OR (v_existing_version = p_client_version AND v_existing_updated_by IS DISTINCT FROM v_uid)
            ) THEN
                RETURN jsonb_build_object(
                    'status', 'CONFLICT',
                    'entity_type', 'PHOTO',
                    'entity_id', p_entity_id,
                    'server_version', v_existing_version,
                    'server_record', v_server_json
                );
            END IF;

            v_new_version := CASE
                WHEN v_existing_version IS NULL THEN GREATEST(1, p_client_version)
                WHEN p_force_overwrite THEN v_existing_version + 1
                ELSE GREATEST(v_existing_version + 1, p_client_version)
            END;

            INSERT INTO public.person_photos (
                id, family_id, person_id, remote_storage_key, sha256,
                description, photo_date, photo_date_precision, media_type,
                created_by_user_id, updated_by_user_id, version, is_deleted, deleted_at, created_at, updated_at
            ) VALUES (
                p_entity_id,
                p_family_id,
                v_person_id,
                COALESCE(NULLIF(p_payload ->> 'remote_storage_key', ''), p_family_id::TEXT || '/photo_' || p_entity_id::TEXT || '.jpg'),
                p_payload ->> 'sha256',
                p_payload ->> 'description',
                p_payload ->> 'photo_date',
                COALESCE(NULLIF(p_payload ->> 'photo_date_precision', ''), 'UNKNOWN'),
                COALESCE(NULLIF(p_payload ->> 'media_type', ''), 'PERSON_PORTRAIT'),
                v_uid,
                v_uid,
                v_new_version,
                v_is_delete,
                v_deleted_at,
                COALESCE((p_payload ->> 'created_at')::BIGINT, v_now),
                v_now
            )
            ON CONFLICT (id) DO UPDATE SET
                person_id = EXCLUDED.person_id,
                remote_storage_key = EXCLUDED.remote_storage_key,
                sha256 = EXCLUDED.sha256,
                description = EXCLUDED.description,
                photo_date = EXCLUDED.photo_date,
                photo_date_precision = EXCLUDED.photo_date_precision,
                media_type = EXCLUDED.media_type,
                updated_by_user_id = v_uid,
                version = v_new_version,
                is_deleted = v_is_delete,
                deleted_at = v_deleted_at,
                updated_at = v_now;

            IF jsonb_typeof(p_payload -> 'tagged_person_ids') = 'array' THEN
                UPDATE public.photo_person_tags
                SET is_deleted = TRUE, updated_at = v_now
                WHERE photo_id = p_entity_id AND family_id = p_family_id;

                FOR v_tag_id IN
                    SELECT jsonb_array_elements_text(p_payload -> 'tagged_person_ids')
                LOOP
                    IF EXISTS (SELECT 1 FROM public.persons p WHERE p.id = v_tag_id::UUID AND p.family_id = p_family_id) THEN
                        INSERT INTO public.photo_person_tags (photo_id, person_id, family_id, is_deleted, updated_at)
                        VALUES (p_entity_id, v_tag_id::UUID, p_family_id, v_is_delete, v_now)
                        ON CONFLICT (photo_id, person_id) DO UPDATE SET
                            is_deleted = v_is_delete,
                            updated_at = v_now;
                    END IF;
                END LOOP;
            END IF;

        WHEN 'FAMILY_MEMBER' THEN
            IF v_caller.role NOT IN ('OWNER', 'ADMIN') THEN
                RAISE EXCEPTION 'Not authorized to sync member links';
            END IF;

            v_person_id := NULLIF(p_payload ->> 'linked_person_id', '')::UUID;
            v_root_person_id := COALESCE(NULLIF(p_payload ->> 'branch_root_person_id', '')::UUID, v_person_id);

            IF v_person_id IS NOT NULL AND EXISTS (
                SELECT 1 FROM public.family_members fm
                WHERE fm.family_id = p_family_id
                  AND fm.linked_person_id = v_person_id
                  AND fm.status = 'ACTIVE'
                  AND fm.id <> p_entity_id
            ) THEN
                RAISE EXCEPTION 'Target person record is already linked to another active account';
            END IF;

            IF v_person_id IS NOT NULL AND NOT EXISTS (
                SELECT 1 FROM public.persons p WHERE p.id = v_person_id AND p.family_id = p_family_id
            ) THEN
                v_person_id := NULL;
            END IF;
            IF v_root_person_id IS NOT NULL AND NOT EXISTS (
                SELECT 1 FROM public.persons p WHERE p.id = v_root_person_id AND p.family_id = p_family_id
            ) THEN
                v_root_person_id := v_person_id;
            END IF;

            UPDATE public.family_members
            SET linked_person_id = v_person_id,
                branch_root_person_id = COALESCE(v_root_person_id, v_person_id),
                updated_at = v_now,
                version = version + 1
            WHERE id = p_entity_id AND family_id = p_family_id
            RETURNING version INTO v_new_version;

            v_new_version := COALESCE(v_new_version, 1);

        ELSE
            RAISE EXCEPTION 'Unsupported sync entity type: %', p_entity_type;
    END CASE;

    INSERT INTO public.audit_logs (
        family_id, actor_user_id, actor_member_role, entity_type, entity_id, operation, old_value, new_value, operation_date
    ) VALUES (
        p_family_id, v_uid, v_caller.role, p_entity_type, p_entity_id::TEXT,
        CASE WHEN v_is_delete THEN 'DELETE' WHEN v_existing_version IS NULL THEN 'CREATE' ELSE 'UPDATE' END,
        NULL, 'sync v' || v_new_version, v_now
    );

    RETURN jsonb_build_object(
        'status', 'APPLIED',
        'entity_type', upper(trim(p_entity_type)),
        'entity_id', p_entity_id,
        'version', v_new_version,
        'updated_at', v_now
    );
END;
$$;

-- ============================================================================
-- Incremental Delta Pull RPC (Respects RLS/Branch Permissions + Soft-Delete Tombstones)
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
            WHERE m.family_id = p_family_id AND m.updated_at > v_since
        ), '[]'::JSONB),
        'parent_child_relationships', COALESCE((
            SELECT jsonb_agg(to_jsonb(r) ORDER BY r.created_at ASC)
            FROM public.parent_child_relationships r
            WHERE r.family_id = p_family_id AND r.updated_at > v_since
        ), '[]'::JSONB),
        'education', COALESCE((
            SELECT jsonb_agg(to_jsonb(e) ORDER BY e.created_at ASC)
            FROM public.education e
            WHERE e.family_id = p_family_id AND e.updated_at > v_since
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
            WHERE pt.family_id = p_family_id AND pt.updated_at > v_since
        ), '[]'::JSONB),
        'events', COALESCE((
            SELECT jsonb_agg(to_jsonb(ev) ORDER BY ev.created_at ASC)
            FROM public.events ev
            WHERE ev.family_id = p_family_id AND ev.updated_at > v_since
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
