package io.github.nandydesikan.eip.returns.infrastructure.persistence;

import io.github.nandydesikan.eip.returns.application.IdempotencyConflict;
import io.github.nandydesikan.eip.returns.application.StartReturnWorkflow;
import io.github.nandydesikan.eip.returns.application.command.StartReturnWorkflowCommand;
import io.github.nandydesikan.eip.returns.application.port.WorkflowRepository;
import io.github.nandydesikan.eip.returns.domain.ReturnWorkflow;
import io.github.nandydesikan.eip.returns.domain.ReturnWorkflowId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("postgres")
class PostgresWorkflowRepositoryIT {

    @Autowired
    private StartReturnWorkflow startReturnWorkflow;

    @Autowired
    private WorkflowRepository workflowRepository;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @BeforeEach
    void clearApplicationTables() {
        jdbc.execute("TRUNCATE outbox_event, workflow_transition, idempotency_record, workflow_instance");
    }

    @Test
    void flywayCreatesTheDurabilitySchema() {
        Integer migrationCount = jdbc.queryForObject(
                "SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class);

        assertThat(migrationCount).isGreaterThanOrEqualTo(1);
    }

    @Test
    void persistsWorkflowIdempotencyTransitionAndOutboxAtomically() {
        var command = command("idem-persist-001", "DAMAGED");

        var result = startReturnWorkflow.handle(command);

        assertThat(result.created()).isTrue();
        assertThat(tableCount("workflow_instance")).isEqualTo(1);
        assertThat(tableCount("idempotency_record")).isEqualTo(1);
        assertThat(tableCount("workflow_transition")).isEqualTo(1);
        assertThat(tableCount("outbox_event")).isEqualTo(1);
        assertThat(workflowRepository.findById(result.workflow().id()))
                .get()
                .extracting(ReturnWorkflow::id)
                .isEqualTo(result.workflow().id());
    }

    @Test
    void replaysTheSameRequestAndRejectsAConflictingRequest() {
        var original = command("idem-replay-001", "DAMAGED");

        var first = startReturnWorkflow.handle(original);
        var replay = startReturnWorkflow.handle(original);

        assertThat(replay.created()).isFalse();
        assertThat(replay.workflow().id()).isEqualTo(first.workflow().id());
        assertThatThrownBy(() -> startReturnWorkflow.handle(
                command("idem-replay-001", "ORDERED_IN_ERROR")))
                .isInstanceOf(IdempotencyConflict.class);
        assertThat(tableCount("workflow_instance")).isEqualTo(1);
        assertThat(tableCount("outbox_event")).isEqualTo(1);
    }

    @Test
    void allowsOnlyOneWinnerUnderAConcurrentIdempotencyRace() throws Exception {
        var command = command("idem-race-001", "DAMAGED");
        var gate = new CountDownLatch(1);

        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                gate.await();
                return startReturnWorkflow.handle(command);
            });
            var second = executor.submit(() -> {
                gate.await();
                return startReturnWorkflow.handle(command);
            });
            gate.countDown();

            var firstResult = first.get();
            var secondResult = second.get();

            assertThat(firstResult.workflow().id()).isEqualTo(secondResult.workflow().id());
            assertThat(firstResult.created() ^ secondResult.created()).isTrue();
            assertThat(tableCount("workflow_instance")).isEqualTo(1);
            assertThat(tableCount("outbox_event")).isEqualTo(1);
        }
    }

    @Test
    void rollsBackEveryDurabilityRecordWhenTheTransactionFails() {
        assertThatThrownBy(() -> transactions.executeWithoutResult(ignored -> {
            var candidate = ReturnWorkflow.start(
                    new ReturnWorkflowId(UUID.fromString("755a914f-f702-43d7-9b14-821150c9b830")),
                    "RET-ROLLBACK",
                    Instant.parse("2026-07-18T12:00:00Z"));
            workflowRepository.saveIfAbsent("idem-rollback-001", "a".repeat(64), candidate);
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(tableCount("workflow_instance")).isZero();
        assertThat(tableCount("idempotency_record")).isZero();
        assertThat(tableCount("workflow_transition")).isZero();
        assertThat(tableCount("outbox_event")).isZero();
    }

    private int tableCount(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private static StartReturnWorkflowCommand command(String key, String reason) {
        return new StartReturnWorkflowCommand(key, "RET-1001", "CUS-42", "ORD-9001", reason);
    }
}
