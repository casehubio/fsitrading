-- C4a: Overnight Incident Timeline table
CREATE TABLE fsi_incident_timeline (
    id          UUID         PRIMARY KEY,
    case_id     UUID         NOT NULL REFERENCES fsi_incident(case_id),
    milestone   VARCHAR(50)  NOT NULL,
    occurred_at TIMESTAMP    NOT NULL,
    description TEXT
);

CREATE INDEX idx_fsi_incident_timeline_case ON fsi_incident_timeline(case_id);
CREATE INDEX idx_fsi_incident_timeline_occurred ON fsi_incident_timeline(occurred_at);
