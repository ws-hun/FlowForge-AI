ALTER TABLE prompts
    ADD COLUMN IF NOT EXISTS revision BIGINT;

UPDATE prompts
SET revision = 0
WHERE revision IS NULL;

ALTER TABLE prompts
    ALTER COLUMN revision SET DEFAULT 0;

ALTER TABLE prompts
    ALTER COLUMN revision SET NOT NULL;
