--liquibase formatted sql
-- PLA-36: admin audit history is append-only, enforced by the database. Row-level trigger;
-- TRUNCATE and the table owner/superuser are out of scope (protection is "through the application").
--changeset plantpal:049-admin-activity-reject-fn splitStatements:false
CREATE FUNCTION admin_activity_reject_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'admin_activity is append-only: % rejected', TG_OP;
END;
$$ LANGUAGE plpgsql;
--rollback DROP FUNCTION admin_activity_reject_mutation();

--changeset plantpal:049-admin-activity-append-only-trigger
CREATE TRIGGER trg_admin_activity_append_only
    BEFORE UPDATE OR DELETE ON admin_activity
    FOR EACH ROW EXECUTE FUNCTION admin_activity_reject_mutation();
--rollback DROP TRIGGER trg_admin_activity_append_only ON admin_activity;
