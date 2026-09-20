CREATE INDEX IF NOT EXISTS ix_prompts_source_task_id
    ON prompts (source_task_id)
    WHERE source_task_id IS NOT NULL;
