package io.github.nandydesikan.eip.returns.domain;

public final class IllegalWorkflowTransition extends RuntimeException {
    public IllegalWorkflowTransition(ReturnWorkflowPhase from, ReturnWorkflowPhase to) {
        super("Illegal return-workflow transition from %s to %s".formatted(from, to));
    }
}
