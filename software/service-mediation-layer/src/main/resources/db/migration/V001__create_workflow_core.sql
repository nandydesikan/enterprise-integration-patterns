CREATE TABLE workflow_instance (
    workflow_id UUID PRIMARY KEY,
    workflow_type VARCHAR(64) NOT NULL,
    business_key VARCHAR(128) NOT NULL,
    phase VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_workflow_business_key UNIQUE (workflow_type, business_key),
    CONSTRAINT ck_workflow_status CHECK (status IN (
        'ACTIVE', 'WAITING', 'RETRY_PENDING', 'RECONCILIATION_REQUIRED',
        'COMPENSATION_REQUIRED', 'COMPLETED', 'FAILED'
    ))
);

CREATE TABLE idempotency_record (
    idempotency_key VARCHAR(128) PRIMARY KEY,
    request_fingerprint CHAR(64) NOT NULL,
    workflow_id UUID NOT NULL,
    response_status VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_idempotency_workflow
        FOREIGN KEY (workflow_id) REFERENCES workflow_instance (workflow_id)
        DEFERRABLE INITIALLY DEFERRED
);

CREATE TABLE workflow_transition (
    transition_id UUID PRIMARY KEY,
    workflow_id UUID NOT NULL REFERENCES workflow_instance (workflow_id),
    from_phase VARCHAR(64),
    to_phase VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE outbox_event (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(64) NOT NULL,
    aggregate_id UUID NOT NULL REFERENCES workflow_instance (workflow_id),
    event_type VARCHAR(128) NOT NULL,
    payload JSONB NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_outbox_attempt_count CHECK (attempt_count >= 0)
);

CREATE INDEX ix_transition_workflow_time
    ON workflow_transition (workflow_id, occurred_at);

CREATE INDEX ix_outbox_unpublished_due
    ON outbox_event (next_attempt_at, occurred_at)
    WHERE published_at IS NULL;
