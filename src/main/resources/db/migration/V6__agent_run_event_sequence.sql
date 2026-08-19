ALTER TABLE agent_run
    ADD COLUMN event_sequence BIGINT NOT NULL DEFAULT 0 AFTER trace_id;

UPDATE agent_run ar
LEFT JOIN (
    SELECT agent_run_id, MAX(sequence) AS max_sequence
    FROM run_event
    GROUP BY agent_run_id
) existing_events ON existing_events.agent_run_id = ar.id
SET ar.event_sequence = COALESCE(existing_events.max_sequence, 0);
