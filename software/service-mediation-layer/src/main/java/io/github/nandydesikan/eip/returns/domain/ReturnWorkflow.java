package io.github.nandydesikan.eip.returns.domain;

import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ReturnWorkflow {

    private static final Map<ReturnWorkflowPhase, Set<ReturnWorkflowPhase>> LEGAL_TRANSITIONS = legalTransitions();

    private final ReturnWorkflowId id;
    private final String returnRequestId;
    private final Instant createdAt;
    private ReturnWorkflowPhase phase;
    private WorkflowStatus status;
    private Instant updatedAt;

    private ReturnWorkflow(
            ReturnWorkflowId id,
            String returnRequestId,
            ReturnWorkflowPhase phase,
            WorkflowStatus status,
            Instant createdAt,
            Instant updatedAt
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.returnRequestId = requireText(returnRequestId, "returnRequestId");
        this.phase = Objects.requireNonNull(phase, "phase");
        this.status = Objects.requireNonNull(status, "status");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static ReturnWorkflow start(ReturnWorkflowId id, String returnRequestId, Instant now) {
        return new ReturnWorkflow(
                id,
                returnRequestId,
                ReturnWorkflowPhase.REQUESTED,
                WorkflowStatus.ACTIVE,
                now,
                now
        );
    }

    public void transitionTo(ReturnWorkflowPhase nextPhase, Instant now) {
        Objects.requireNonNull(nextPhase, "nextPhase");
        Objects.requireNonNull(now, "now");
        if (!LEGAL_TRANSITIONS.getOrDefault(phase, Set.of()).contains(nextPhase)) {
            throw new IllegalWorkflowTransition(phase, nextPhase);
        }

        phase = nextPhase;
        status = switch (nextPhase) {
            case AWAITING_ITEM_RECEIPT -> WorkflowStatus.WAITING;
            case COMPLETED, REJECTED, CANCELLED -> WorkflowStatus.COMPLETED;
            default -> WorkflowStatus.ACTIVE;
        };
        updatedAt = now;
    }

    public ReturnWorkflowId id() {
        return id;
    }

    public String returnRequestId() {
        return returnRequestId;
    }

    public ReturnWorkflowPhase phase() {
        return phase;
    }

    public WorkflowStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    private static Map<ReturnWorkflowPhase, Set<ReturnWorkflowPhase>> legalTransitions() {
        var transitions = new EnumMap<ReturnWorkflowPhase, Set<ReturnWorkflowPhase>>(ReturnWorkflowPhase.class);
        transitions.put(ReturnWorkflowPhase.REQUESTED,
                EnumSet.of(ReturnWorkflowPhase.ELIGIBILITY_CONFIRMED, ReturnWorkflowPhase.REJECTED));
        transitions.put(ReturnWorkflowPhase.ELIGIBILITY_CONFIRMED,
                EnumSet.of(ReturnWorkflowPhase.RETURN_SHIPMENT_RESERVED, ReturnWorkflowPhase.CANCELLED));
        transitions.put(ReturnWorkflowPhase.RETURN_SHIPMENT_RESERVED,
                EnumSet.of(ReturnWorkflowPhase.AWAITING_ITEM_RECEIPT, ReturnWorkflowPhase.CANCELLED));
        transitions.put(ReturnWorkflowPhase.AWAITING_ITEM_RECEIPT,
                EnumSet.of(ReturnWorkflowPhase.ITEM_RECEIVED, ReturnWorkflowPhase.CANCELLED));
        transitions.put(ReturnWorkflowPhase.ITEM_RECEIVED,
                EnumSet.of(ReturnWorkflowPhase.REFUND_CONFIRMED));
        transitions.put(ReturnWorkflowPhase.REFUND_CONFIRMED,
                EnumSet.of(ReturnWorkflowPhase.COMPLETED));
        return Map.copyOf(transitions);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
