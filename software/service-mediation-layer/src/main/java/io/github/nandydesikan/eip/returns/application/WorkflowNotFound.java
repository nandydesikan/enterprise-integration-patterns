package io.github.nandydesikan.eip.returns.application;

import io.github.nandydesikan.eip.returns.domain.ReturnWorkflowId;

public final class WorkflowNotFound extends RuntimeException {
    public WorkflowNotFound(ReturnWorkflowId workflowId) {
        super("Return workflow %s was not found".formatted(workflowId));
    }
}
