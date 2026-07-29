CREATE FUNCTION pg_temp.flowforge_add_column_if_missing(
    p_table_name TEXT,
    p_column_name TEXT,
    p_column_definition TEXT
) RETURNS VOID AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = p_table_name
          AND column_name = p_column_name
    ) THEN
        EXECUTE format(
            'ALTER TABLE %I ADD COLUMN %I %s',
            p_table_name,
            p_column_name,
            p_column_definition
        );
    END IF;
END;
$$ LANGUAGE plpgsql;

CREATE TABLE IF NOT EXISTS tasks (
    id UUID PRIMARY KEY,
    input TEXT NOT NULL,
    result TEXT NOT NULL,
    summary TEXT NOT NULL,
    provider VARCHAR(40),
    model VARCHAR(120),
    input_tokens INTEGER,
    output_tokens INTEGER,
    total_tokens INTEGER,
    rerun_of_task_id UUID,
    continued_from_task_id UUID,
    input_variant_of_task_id UUID,
    status VARCHAR(20),
    error_message TEXT,
    duration_ms BIGINT,
    source_prompt_id UUID,
    source_prompt_title VARCHAR(120),
    source_flow_id UUID,
    source_flow_title VARCHAR(120),
    source_flow_snapshot_json TEXT,
    flow_run_trace_json TEXT,
    created_at TIMESTAMP(6) NOT NULL
);

DO $$
BEGIN
    PERFORM pg_temp.flowforge_add_column_if_missing('tasks', 'provider', 'VARCHAR(40)');
    PERFORM pg_temp.flowforge_add_column_if_missing('tasks', 'model', 'VARCHAR(120)');
    PERFORM pg_temp.flowforge_add_column_if_missing('tasks', 'input_tokens', 'INTEGER');
    PERFORM pg_temp.flowforge_add_column_if_missing('tasks', 'output_tokens', 'INTEGER');
    PERFORM pg_temp.flowforge_add_column_if_missing('tasks', 'total_tokens', 'INTEGER');
    PERFORM pg_temp.flowforge_add_column_if_missing('tasks', 'rerun_of_task_id', 'UUID');
    PERFORM pg_temp.flowforge_add_column_if_missing('tasks', 'continued_from_task_id', 'UUID');
    PERFORM pg_temp.flowforge_add_column_if_missing('tasks', 'input_variant_of_task_id', 'UUID');
    PERFORM pg_temp.flowforge_add_column_if_missing('tasks', 'status', 'VARCHAR(20)');
    PERFORM pg_temp.flowforge_add_column_if_missing('tasks', 'error_message', 'TEXT');
    PERFORM pg_temp.flowforge_add_column_if_missing('tasks', 'duration_ms', 'BIGINT');
    PERFORM pg_temp.flowforge_add_column_if_missing('tasks', 'source_prompt_id', 'UUID');
    PERFORM pg_temp.flowforge_add_column_if_missing('tasks', 'source_prompt_title', 'VARCHAR(120)');
    PERFORM pg_temp.flowforge_add_column_if_missing('tasks', 'source_flow_id', 'UUID');
    PERFORM pg_temp.flowforge_add_column_if_missing('tasks', 'source_flow_title', 'VARCHAR(120)');
    PERFORM pg_temp.flowforge_add_column_if_missing('tasks', 'source_flow_snapshot_json', 'TEXT');
    PERFORM pg_temp.flowforge_add_column_if_missing('tasks', 'flow_run_trace_json', 'TEXT');
END;
$$;

CREATE TABLE IF NOT EXISTS ai_api_keys (
    id UUID PRIMARY KEY,
    provider VARCHAR(40) NOT NULL,
    api_key TEXT NOT NULL,
    base_url VARCHAR(300) NOT NULL,
    model VARCHAR(120) NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_ai_api_keys_provider ON ai_api_keys (provider);

CREATE TABLE IF NOT EXISTS prompts (
    id UUID PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    category VARCHAR(80) NOT NULL,
    description VARCHAR(300) NOT NULL,
    content TEXT NOT NULL,
    tags TEXT NOT NULL,
    favorite BOOLEAN NOT NULL,
    source_task_id UUID,
    source_task_summary TEXT,
    source_prompt_id UUID,
    source_prompt_title VARCHAR(120),
    source_flow_id UUID,
    source_flow_title VARCHAR(120),
    source_node_id VARCHAR(80),
    source_node_title VARCHAR(120),
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

DO $$
BEGIN
    PERFORM pg_temp.flowforge_add_column_if_missing('prompts', 'source_task_id', 'UUID');
    PERFORM pg_temp.flowforge_add_column_if_missing('prompts', 'source_task_summary', 'TEXT');
    PERFORM pg_temp.flowforge_add_column_if_missing('prompts', 'source_prompt_id', 'UUID');
    PERFORM pg_temp.flowforge_add_column_if_missing('prompts', 'source_prompt_title', 'VARCHAR(120)');
    PERFORM pg_temp.flowforge_add_column_if_missing('prompts', 'source_flow_id', 'UUID');
    PERFORM pg_temp.flowforge_add_column_if_missing('prompts', 'source_flow_title', 'VARCHAR(120)');
    PERFORM pg_temp.flowforge_add_column_if_missing('prompts', 'source_node_id', 'VARCHAR(80)');
    PERFORM pg_temp.flowforge_add_column_if_missing('prompts', 'source_node_title', 'VARCHAR(120)');
END;
$$;

CREATE TABLE IF NOT EXISTS prompt_versions (
    id UUID PRIMARY KEY,
    prompt_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    title VARCHAR(120) NOT NULL,
    category VARCHAR(80) NOT NULL,
    description VARCHAR(300) NOT NULL,
    content TEXT NOT NULL,
    tags TEXT NOT NULL,
    favorite BOOLEAN NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_prompt_versions_asset_version
    ON prompt_versions (prompt_id, version_number);

CREATE TABLE IF NOT EXISTS flows (
    id UUID PRIMARY KEY,
    title VARCHAR(120) NOT NULL,
    description TEXT NOT NULL,
    nodes_json TEXT NOT NULL,
    source_flow_id UUID,
    source_flow_title VARCHAR(120),
    source_flow_version_id UUID,
    source_flow_version_number INTEGER,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL
);

DO $$
BEGIN
    PERFORM pg_temp.flowforge_add_column_if_missing('flows', 'source_flow_id', 'UUID');
    PERFORM pg_temp.flowforge_add_column_if_missing('flows', 'source_flow_title', 'VARCHAR(120)');
    PERFORM pg_temp.flowforge_add_column_if_missing('flows', 'source_flow_version_id', 'UUID');
    PERFORM pg_temp.flowforge_add_column_if_missing('flows', 'source_flow_version_number', 'INTEGER');
END;
$$;

CREATE TABLE IF NOT EXISTS flow_versions (
    id UUID PRIMARY KEY,
    flow_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    title VARCHAR(120) NOT NULL,
    description TEXT NOT NULL,
    nodes_json TEXT NOT NULL,
    created_at TIMESTAMP(6) NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_flow_versions_asset_version
    ON flow_versions (flow_id, version_number);

CREATE INDEX IF NOT EXISTS ix_tasks_created_at ON tasks (created_at DESC);
CREATE INDEX IF NOT EXISTS ix_tasks_source_prompt_created_at
    ON tasks (source_prompt_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_tasks_source_flow_created_at
    ON tasks (source_flow_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_prompts_updated_at ON prompts (updated_at DESC);
CREATE INDEX IF NOT EXISTS ix_flows_updated_at ON flows (updated_at DESC);
CREATE INDEX IF NOT EXISTS ix_prompt_versions_prompt_id
    ON prompt_versions (prompt_id, version_number DESC);
CREATE INDEX IF NOT EXISTS ix_flow_versions_flow_id
    ON flow_versions (flow_id, version_number DESC);
