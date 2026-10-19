CREATE TABLE IF NOT EXISTS status_transition_causes (
    id INT PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description VARCHAR(255)
);

INSERT INTO status_transition_causes (id, name, description) VALUES
(1, 'USER', 'Direct transition triggered explicitly by the user.'),
(2, 'UPWARD_CASCADE', 'Automatic transition triggered upwards by an event on a child goal.'),
(3, 'DOWNWARD_CASCADE', 'Automatic transition triggered downwards by an event on a parent goal.')
ON CONFLICT (id) DO UPDATE SET 
    name = EXCLUDED.name, 
    description = EXCLUDED.description;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_type WHERE typname = 'status_transition_cause_type') THEN
        ALTER DOMAIN status_transition_cause_type DROP CONSTRAINT check_transition_cause_valid;
        ALTER DOMAIN status_transition_cause_type ADD CONSTRAINT check_transition_cause_valid CHECK (VALUE IN (1, 2, 3));
    ELSE
        CREATE DOMAIN status_transition_cause_type AS INT
            CONSTRAINT check_transition_cause_valid
            CHECK (VALUE IN (1, 2, 3));
    END IF;
END $$;