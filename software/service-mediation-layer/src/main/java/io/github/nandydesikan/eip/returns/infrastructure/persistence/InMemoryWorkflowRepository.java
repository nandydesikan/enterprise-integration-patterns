package io.github.nandydesikan.eip.returns.infrastructure.persistence;

import io.github.nandydesikan.eip.returns.application.IdempotencyConflict;
import io.github.nandydesikan.eip.returns.application.port.WorkflowRepository;
import io.github.nandydesikan.eip.returns.domain.ReturnWorkflow;
import io.github.nandydesikan.eip.returns.domain.ReturnWorkflowId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Repository
@ConditionalOnProperty(prefix = "mediation", name = "persistence-mode", havingValue = "in-memory")
public class InMemoryWorkflowRepository implements WorkflowRepository {

    private final Map<String, IdempotencyEntry> idempotencyEntries = new HashMap<>();
    private final Map<ReturnWorkflowId, ReturnWorkflow> workflows = new HashMap<>();

    @Override
    public synchronized StartResult saveIfAbsent(
            String idempotencyKey,
            String requestFingerprint,
            ReturnWorkflow candidate
    ) {
        var existing = idempotencyEntries.get(idempotencyKey);
        if (existing != null) {
            if (!existing.requestFingerprint().equals(requestFingerprint)) {
                throw new IdempotencyConflict();
            }
            return new StartResult(workflows.get(existing.workflowId()), false);
        }

        workflows.put(candidate.id(), candidate);
        idempotencyEntries.put(idempotencyKey, new IdempotencyEntry(requestFingerprint, candidate.id()));
        return new StartResult(candidate, true);
    }

    @Override
    public synchronized Optional<ReturnWorkflow> findById(ReturnWorkflowId workflowId) {
        return Optional.ofNullable(workflows.get(workflowId));
    }

    private record IdempotencyEntry(String requestFingerprint, ReturnWorkflowId workflowId) {
    }
}
