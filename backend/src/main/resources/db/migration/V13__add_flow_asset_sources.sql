ALTER TABLE flows
    ADD COLUMN IF NOT EXISTS source_task_id UUID,
    ADD COLUMN IF NOT EXISTS source_task_summary TEXT,
    ADD COLUMN IF NOT EXISTS source_prompt_id UUID,
    ADD COLUMN IF NOT EXISTS source_prompt_title VARCHAR(120);

CREATE INDEX IF NOT EXISTS ix_flows_source_task_id
    ON flows (source_task_id)
    WHERE source_task_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_flows_source_prompt_id
    ON flows (source_prompt_id)
    WHERE source_prompt_id IS NOT NULL;
