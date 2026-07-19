package io.github.nandydesikan.eip.returns.domain;

public enum WorkflowStatus {
    ACTIVE,
    WAITING,
    RETRY_PENDING,
    RECONCILIATION_REQUIRED,
    COMPENSATION_REQUIRED,
    COMPLETED,
    FAILED
}
