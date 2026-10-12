CREATE TABLE goal_logs (
    id BIGSERIAL PRIMARY KEY,
    goal_id BIGINT NOT NULL,
    log_date DATE NOT NULL,
    successful BOOLEAN NOT NULL DEFAULT TRUE,
    notes TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_goal_logs_goal FOREIGN KEY (goal_id) 
        REFERENCES goals (id) ON DELETE CASCADE,

    CONSTRAINT uk_goal_log_per_day UNIQUE (goal_id, log_date)
);

CREATE INDEX idx_goal_logs_goal_date ON goal_logs (goal_id, log_date);