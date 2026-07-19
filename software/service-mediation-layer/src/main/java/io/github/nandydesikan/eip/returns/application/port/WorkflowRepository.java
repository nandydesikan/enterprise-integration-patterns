package io.github.nandydesikan.eip.returns.application.port;

import io.github.nandydesikan.eip.returns.domain.ReturnWorkflow;
import io.github.nandydesikan.eip.returns.domain.ReturnWorkflowId;

import java.util.Optional;

public interface WorkflowRepository {

    StartResult saveIfAbsent(String idempotencyKey, String requestFingerprint, ReturnWorkflow candidate);

    Optional<ReturnWorkflow> findById(ReturnWorkflowId workflowId);

    record StartResult(ReturnWorkflow workflow, boolean created) {
    }
}
