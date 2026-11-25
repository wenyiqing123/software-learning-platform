-- V17: Persist monotonic peak mastery per user and knowledge point for peak-locked prerequisite unlocking.
ALTER TABLE knowledge_mastery
    ADD COLUMN peak_mastery DECIMAL(5,2) NULL AFTER mastery_score;

UPDATE knowledge_mastery
SET peak_mastery = mastery_score
WHERE peak_mastery IS NULL;

ALTER TABLE knowledge_mastery
    MODIFY COLUMN peak_mastery DECIMAL(5,2) NOT NULL DEFAULT 0.00;
