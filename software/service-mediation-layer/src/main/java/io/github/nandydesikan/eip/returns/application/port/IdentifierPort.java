package io.github.nandydesikan.eip.returns.application.port;

import io.github.nandydesikan.eip.returns.domain.ReturnWorkflowId;

@FunctionalInterface
public interface IdentifierPort {
    ReturnWorkflowId nextWorkflowId();
}
