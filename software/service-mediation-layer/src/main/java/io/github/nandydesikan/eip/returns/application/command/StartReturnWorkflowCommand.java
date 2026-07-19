package io.github.nandydesikan.eip.returns.application.command;

public record StartReturnWorkflowCommand(
        String idempotencyKey,
        String returnRequestId,
        String customerId,
        String orderId,
        String reason
) {
}
