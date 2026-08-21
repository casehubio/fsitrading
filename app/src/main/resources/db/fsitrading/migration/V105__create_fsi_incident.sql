-- C4a: Overnight Incident table
CREATE TABLE fsi_incident (
    case_id      UUID         PRIMARY KEY,
    severity     VARCHAR(20)  NOT NULL,
    event_type   VARCHAR(30)  NOT NULL,
    instruments  TEXT         NOT NULL,
    status       VARCHAR(30)  NOT NULL,
    created_at   TIMESTAMP    NOT NULL,
    resolved_at  TIMESTAMP
);

CREATE INDEX idx_fsi_incident_status ON fsi_incident(status);
CREATE INDEX idx_fsi_incident_severity ON fsi_incident(severity);
CREATE INDEX idx_fsi_incident_created ON fsi_incident(created_at);
