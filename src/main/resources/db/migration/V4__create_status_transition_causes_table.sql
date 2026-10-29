CREATE TABLE IF NOT EXISTS status_transition_causes (
    id INT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255)
);

INSERT INTO status_transition_causes (id, name, description) VALUES
(1, 'CREATION', 'Automatic event registered when the goal is initially created.'),
(2, 'USER', 'Direct transition triggered explicitly by the user.'),
(3, 'UPWARD_CASCADE', 'Automatic transition triggered upwards by an event on a child goal.'),
(4, 'DOWNWARD_CASCADE', 'Automatic transition triggered downwards by an event on a parent goal.'),
(5, 'AUTO_ACTIVATION', 'Automatic transition triggered when the goal automatically starts based on schedule.'),
(6, 'AUTO_FAILURE', 'Automatic transition triggered when the goal fails due to expiration or unmet deadlines.')
ON CONFLICT (id) DO UPDATE SET 
    name = EXCLUDED.name, 
    description = EXCLUDED.description;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'status_transition_cause_type') THEN
        ALTER DOMAIN status_transition_cause_type DROP CONSTRAINT check_transition_cause_valid;
        ALTER DOMAIN status_transition_cause_type ADD CONSTRAINT check_transition_cause_valid CHECK (VALUE IN (1, 2, 3, 4, 5, 6));
    ELSE
        CREATE DOMAIN status_transition_cause_type AS INT
            CONSTRAINT check_transition_cause_valid
            CHECK (VALUE IN (1, 2, 3, 4, 5, 6));
    END IF;
END $$;