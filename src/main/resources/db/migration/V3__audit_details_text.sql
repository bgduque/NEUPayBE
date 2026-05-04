ALTER TABLE audit_logs ALTER COLUMN details TYPE TEXT USING details::text;
