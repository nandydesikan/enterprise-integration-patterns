package io.github.nandydesikan.eip.returns.application;

import io.github.nandydesikan.eip.returns.application.command.StartReturnWorkflowCommand;
import io.github.nandydesikan.eip.returns.domain.ReturnWorkflowId;
import io.github.nandydesikan.eip.returns.infrastructure.persistence.InMemoryWorkflowRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StartReturnWorkflowTest {

    private static final ReturnWorkflowId WORKFLOW_ID = new ReturnWorkflowId(
            UUID.fromString("87601455-d786-40ee-aa20-7c4430e81b30"));

    private StartReturnWorkflow useCase;

    @BeforeEach
    void setUp() {
        useCase = new StartReturnWorkflow(
                new InMemoryWorkflowRepository(),
                () -> WORKFLOW_ID,
                () -> Instant.parse("2026-07-18T12:00:00Z"));
    }

    @Test
    void returnsTheSameWorkflowForTheSameKeyAndSemanticRequest() {
        var command = command("idem-001", "DAMAGED");

        var first = useCase.handle(command);
        var replay = useCase.handle(command);

        assertThat(first.created()).isTrue();
        assertThat(replay.created()).isFalse();
        assertThat(replay.workflow().id()).isEqualTo(first.workflow().id());
    }

    @Test
    void rejectsReuseOfAKeyForADifferentSemanticRequest() {
        useCase.handle(command("idem-002", "DAMAGED"));

        assertThatThrownBy(() -> useCase.handle(command("idem-002", "ORDERED_IN_ERROR")))
                .isInstanceOf(IdempotencyConflict.class);
    }

    private static StartReturnWorkflowCommand command(String key, String reason) {
        return new StartReturnWorkflowCommand(key, "RET-1001", "CUS-42", "ORD-9001", reason);
    }
}
