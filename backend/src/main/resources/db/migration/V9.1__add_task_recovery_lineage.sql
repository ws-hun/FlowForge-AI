ALTER TABLE tasks
    ADD COLUMN recovery_of_task_id UUID;

CREATE INDEX ix_tasks_recovery_of_task_id_created_at
    ON tasks (recovery_of_task_id, created_at DESC)
    WHERE recovery_of_task_id IS NOT NULL;
