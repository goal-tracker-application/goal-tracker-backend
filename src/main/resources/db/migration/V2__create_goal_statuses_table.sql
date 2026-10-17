CREATE TABLE IF NOT EXISTS goal_statuses (
    id INT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255)
);

INSERT INTO goal_statuses (id, name, description) VALUES
(1, 'WAITING', 'The goal is created or scheduled and awaiting activation.'),
(2, 'IN_PROGRESS', 'The goal is actively being pursued within its execution timeframe.'),
(3, 'PAUSED', 'The goal execution is temporarily frozen; time elapsed is deferred upon unpausing.'),
(4, 'COMPLETED', 'The goal objective was successfully achieved.'),
(5, 'FAILED', 'The goal was missed or expired past its end date without completion.'),
(6, 'DISCARDED', 'The goal was dropped or cancelled without being marked as a failure.')
ON CONFLICT (id) DO UPDATE SET 
    name = EXCLUDED.name, 
    description = EXCLUDED.description;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'goal_status_type') THEN
        ALTER DOMAIN goal_status_type DROP CONSTRAINT check_goal_status_valid;
        ALTER DOMAIN goal_status_type ADD CONSTRAINT check_goal_status_valid CHECK (VALUE IN (1, 2, 3, 4, 5, 6));
    ELSE
        CREATE DOMAIN goal_status_type AS INT
            CONSTRAINT check_goal_status_valid
            CHECK (VALUE IN (1, 2, 3, 4, 5, 6));
    END IF;
END $$;