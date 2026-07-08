ALTER TABLE recurring_occurrence
    ADD COLUMN amount DECIMAL(19,2) DEFAULT NULL AFTER status;
