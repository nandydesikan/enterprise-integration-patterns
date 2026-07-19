package io.github.nandydesikan.eip.returns.infrastructure.persistence;

import io.github.nandydesikan.eip.returns.application.IdempotencyConflict;
import io.github.nandydesikan.eip.returns.application.port.WorkflowRepository;
import io.github.nandydesikan.eip.returns.domain.ReturnWorkflow;
import io.github.nandydesikan.eip.returns.domain.ReturnWorkflowId;
import io.github.nandydesikan.eip.returns.domain.ReturnWorkflowPhase;
import io.github.nandydesikan.eip.returns.domain.WorkflowStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnProperty(prefix = "mediation", name = "persistence-mode", havingValue = "postgres")
public class PostgresWorkflowRepository implements WorkflowRepository {

    private static final String WORKFLOW_COLUMNS = """
            wi.workflow_id, wi.business_key, wi.phase, wi.status, wi.created_at, wi.updated_at
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public PostgresWorkflowRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public StartResult saveIfAbsent(
            String idempotencyKey,
            String requestFingerprint,
            ReturnWorkflow candidate
    ) {
        var parameters = new MapSqlParameterSource()
                .addValue("idempotencyKey", idempotencyKey)
                .addValue("requestFingerprint", requestFingerprint)
                .addValue("workflowId", candidate.id().value())
                .addValue("businessKey", candidate.returnRequestId())
                .addValue("phase", candidate.phase().name())
                .addValue("status", candidate.status().name())
                .addValue("createdAt", candidate.createdAt().atOffset(ZoneOffset.UTC))
                .addValue("updatedAt", candidate.updatedAt().atOffset(ZoneOffset.UTC));

        int claimed = jdbc.update("""
                INSERT INTO idempotency_record (
                    idempotency_key, request_fingerprint, workflow_id, response_status, created_at
                ) VALUES (
                    :idempotencyKey, :requestFingerprint, :workflowId, 'ACCEPTED', :createdAt
                )
                ON CONFLICT (idempotency_key) DO NOTHING
                """, parameters);

        if (claimed == 0) {
            return existingResult(idempotencyKey, requestFingerprint);
        }

        jdbc.update("""
                INSERT INTO workflow_instance (
                    workflow_id, workflow_type, business_key, phase, status, created_at, updated_at
                ) VALUES (
                    :workflowId, 'RETURN', :businessKey, :phase, :status, :createdAt, :updatedAt
                )
                """, parameters);

        parameters.addValue("transitionId", UUID.randomUUID());
        jdbc.update("""
                INSERT INTO workflow_transition (
                    transition_id, workflow_id, from_phase, to_phase, occurred_at
                ) VALUES (
                    :transitionId, :workflowId, NULL, :phase, :createdAt
                )
                """, parameters);

        parameters.addValue("eventId", UUID.randomUUID());
        jdbc.update("""
                INSERT INTO outbox_event (
                    event_id, aggregate_type, aggregate_id, event_type, payload,
                    occurred_at, next_attempt_at
                ) VALUES (
                    :eventId,
                    'RETURN_WORKFLOW',
                    :workflowId,
                    'ReturnWorkflowStarted',
                    jsonb_build_object(
                        'workflowId', CAST(:workflowId AS text),
                        'returnRequestId', :businessKey,
                        'phase', :phase
                    ),
                    :createdAt,
                    :createdAt
                )
                """, parameters);

        return new StartResult(candidate, true);
    }

    @Override
    public Optional<ReturnWorkflow> findById(ReturnWorkflowId workflowId) {
        return jdbc.query("""
                        SELECT %s
                        FROM workflow_instance wi
                        WHERE wi.workflow_id = :workflowId
                        """.formatted(WORKFLOW_COLUMNS),
                new MapSqlParameterSource("workflowId", workflowId.value()),
                PostgresWorkflowRepository::mapWorkflow).stream().findFirst();
    }

    private StartResult existingResult(String idempotencyKey, String requestFingerprint) {
        var existing = jdbc.query("""
                        SELECT ir.request_fingerprint, %s
                        FROM idempotency_record ir
                        JOIN workflow_instance wi ON wi.workflow_id = ir.workflow_id
                        WHERE ir.idempotency_key = :idempotencyKey
                        """.formatted(WORKFLOW_COLUMNS),
                new MapSqlParameterSource("idempotencyKey", idempotencyKey),
                (resultSet, rowNumber) -> new ExistingWorkflow(
                        resultSet.getString("request_fingerprint"),
                        mapWorkflow(resultSet, rowNumber))).stream().findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Idempotency claim exists without its workflow"));

        if (!existing.requestFingerprint().equals(requestFingerprint)) {
            throw new IdempotencyConflict();
        }
        return new StartResult(existing.workflow(), false);
    }

    private static ReturnWorkflow mapWorkflow(ResultSet resultSet, int rowNumber) throws SQLException {
        return ReturnWorkflow.rehydrate(
                new ReturnWorkflowId(resultSet.getObject("workflow_id", UUID.class)),
                resultSet.getString("business_key"),
                ReturnWorkflowPhase.valueOf(resultSet.getString("phase")),
                WorkflowStatus.valueOf(resultSet.getString("status")),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant());
    }

    private record ExistingWorkflow(String requestFingerprint, ReturnWorkflow workflow) {
    }
}
