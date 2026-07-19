package io.github.nandydesikan.eip.returns.api.response;

import io.github.nandydesikan.eip.returns.domain.ReturnWorkflow;
import io.github.nandydesikan.eip.returns.domain.ReturnWorkflowPhase;
import io.github.nandydesikan.eip.returns.domain.WorkflowStatus;

import java.time.Instant;
import java.util.UUID;

public record ReturnWorkflowResponse(
        UUID workflowId,
        String returnRequestId,
        ReturnWorkflowPhase phase,
        WorkflowStatus status,
        boolean idempotentReplay,
        Instant createdAt,
        Instant updatedAt
) {
    public static ReturnWorkflowResponse from(ReturnWorkflow workflow, boolean idempotentReplay) {
        return new ReturnWorkflowResponse(
                workflow.id().value(),
                workflow.returnRequestId(),
                workflow.phase(),
                workflow.status(),
                idempotentReplay,
                workflow.createdAt(),
                workflow.updatedAt());
    }
}
