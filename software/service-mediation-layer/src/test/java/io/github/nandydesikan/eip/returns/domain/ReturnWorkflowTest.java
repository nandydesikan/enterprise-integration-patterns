package io.github.nandydesikan.eip.returns.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReturnWorkflowTest {

    private static final Instant STARTED_AT = Instant.parse("2026-07-18T12:00:00Z");

    @Test
    void completesOnlyThroughTheExplicitBusinessSequence() {
        var workflow = ReturnWorkflow.start(
                new ReturnWorkflowId(UUID.fromString("a5f5573b-30f6-4ab5-ad30-849a310544ad")),
                "RET-1001",
                STARTED_AT);

        workflow.transitionTo(ReturnWorkflowPhase.ELIGIBILITY_CONFIRMED, STARTED_AT.plusSeconds(1));
        workflow.transitionTo(ReturnWorkflowPhase.RETURN_SHIPMENT_RESERVED, STARTED_AT.plusSeconds(2));
        workflow.transitionTo(ReturnWorkflowPhase.AWAITING_ITEM_RECEIPT, STARTED_AT.plusSeconds(3));

        assertThat(workflow.phase()).isEqualTo(ReturnWorkflowPhase.AWAITING_ITEM_RECEIPT);
        assertThat(workflow.status()).isEqualTo(WorkflowStatus.WAITING);

        workflow.transitionTo(ReturnWorkflowPhase.ITEM_RECEIVED, STARTED_AT.plusSeconds(4));
        workflow.transitionTo(ReturnWorkflowPhase.REFUND_CONFIRMED, STARTED_AT.plusSeconds(5));
        workflow.transitionTo(ReturnWorkflowPhase.COMPLETED, STARTED_AT.plusSeconds(6));

        assertThat(workflow.phase()).isEqualTo(ReturnWorkflowPhase.COMPLETED);
        assertThat(workflow.status()).isEqualTo(WorkflowStatus.COMPLETED);
    }

    @Test
    void rejectsAJumpThatWouldSkipParticipantConfirmation() {
        var workflow = ReturnWorkflow.start(
                new ReturnWorkflowId(UUID.randomUUID()),
                "RET-1002",
                STARTED_AT);

        assertThatThrownBy(() -> workflow.transitionTo(ReturnWorkflowPhase.COMPLETED, STARTED_AT.plusSeconds(1)))
                .isInstanceOf(IllegalWorkflowTransition.class)
                .hasMessageContaining("REQUESTED")
                .hasMessageContaining("COMPLETED");

        assertThat(workflow.phase()).isEqualTo(ReturnWorkflowPhase.REQUESTED);
        assertThat(workflow.status()).isEqualTo(WorkflowStatus.ACTIVE);
    }

    @Test
    void treatsBusinessRejectionAsATerminalOutcomeRatherThanATechnicalFailure() {
        var workflow = ReturnWorkflow.start(
                new ReturnWorkflowId(UUID.randomUUID()),
                "RET-1003",
                STARTED_AT);

        workflow.transitionTo(ReturnWorkflowPhase.REJECTED, STARTED_AT.plusSeconds(1));

        assertThat(workflow.phase()).isEqualTo(ReturnWorkflowPhase.REJECTED);
        assertThat(workflow.status()).isEqualTo(WorkflowStatus.COMPLETED);
    }
}
