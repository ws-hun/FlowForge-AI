CREATE TABLE flow_canvas_states
(
    flow_id    UUID PRIMARY KEY REFERENCES flows (id) ON DELETE CASCADE,
    state_json TEXT        NOT NULL,
    revision   BIGINT      NOT NULL DEFAULT 0,
    updated_at TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP
);
