package io.github.nandydesikan.eip.returns.domain;

import java.util.Objects;
import java.util.UUID;

public record ReturnWorkflowId(UUID value) {
    public ReturnWorkflowId {
        Objects.requireNonNull(value, "value");
    }

    public static ReturnWorkflowId from(String value) {
        return new ReturnWorkflowId(UUID.fromString(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
