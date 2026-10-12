CREATE TABLE IF NOT EXISTS goals (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    parent_id BIGINT,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    start_date DATE,
    end_date DATE,
    status_id goal_status_type NOT NULL DEFAULT 1,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_goals_user FOREIGN KEY (user_id) 
        REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_goals_parent FOREIGN KEY (parent_id) 
        REFERENCES goals (id) ON DELETE CASCADE,
    CONSTRAINT fk_goals_status FOREIGN KEY (status_id) 
        REFERENCES goal_statuses (id)
);

CREATE INDEX IF NOT EXISTS idx_goals_user_id ON goals (user_id);
CREATE INDEX IF NOT EXISTS idx_goals_parent_id ON goals (parent_id);
CREATE INDEX IF NOT EXISTS idx_goals_status_id ON goals (status_id);