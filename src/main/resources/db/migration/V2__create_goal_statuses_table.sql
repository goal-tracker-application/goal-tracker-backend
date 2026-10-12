CREATE TABLE IF NOT EXISTS goal_statuses (
    id INT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255)
);

INSERT INTO goal_statuses (id, name, description) VALUES
(1, 'IN_PROGRESS', 'The goal is active and under development.'),
(2, 'COMPLETED', 'The goal was fulfilled and successfully completed.'),
(3, 'ARCHIVED', 'The goal was archived to preserve history without affecting the active dashboard.')
ON CONFLICT (id) DO UPDATE SET 
    name = EXCLUDED.name, 
    description = EXCLUDED.description;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_type WHERE typname = 'goal_status_type') THEN
        CREATE DOMAIN goal_status_type AS INT
            CONSTRAINT check_goal_status_valid
            CHECK (VALUE IN (1, 2, 3));
    END IF;
END $$;