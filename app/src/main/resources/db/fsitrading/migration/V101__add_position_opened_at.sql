-- C1: Quality dimension scoring requires tracking when positions were opened
ALTER TABLE position ADD COLUMN opened_at TIMESTAMP;
