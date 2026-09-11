-- Report suggestions allow 300 characters; preserve them without truncation.
ALTER TABLE study_task MODIFY COLUMN title VARCHAR(300) NOT NULL;
