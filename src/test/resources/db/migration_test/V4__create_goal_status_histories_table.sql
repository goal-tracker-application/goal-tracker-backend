CREATE TABLE IF NOT EXISTS goal_status_histories (
    id BIGSERIAL PRIMARY KEY,
    goal_id BIGINT NOT NULL,
    status_id goal_status_type NOT NULL,
    notes TEXT,
    changed_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_goal_status_histories_goal FOREIGN KEY (goal_id) 
        REFERENCES goals (id) ON DELETE CASCADE,
    CONSTRAINT fk_goal_status_histories_status FOREIGN KEY (status_id) 
        REFERENCES goal_statuses (id)
);

CREATE INDEX IF NOT EXISTS idx_goal_status_histories_goal_id 
    ON goal_status_histories (goal_id);

CREATE INDEX IF NOT EXISTS idx_goal_status_histories_goal_date 
    ON goal_status_histories (goal_id, changed_at ASC);