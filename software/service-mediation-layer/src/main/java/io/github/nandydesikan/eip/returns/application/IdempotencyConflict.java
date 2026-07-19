package io.github.nandydesikan.eip.returns.application;

public final class IdempotencyConflict extends RuntimeException {
    public IdempotencyConflict() {
        super("The Idempotency-Key was already used for a semantically different request");
    }
}
