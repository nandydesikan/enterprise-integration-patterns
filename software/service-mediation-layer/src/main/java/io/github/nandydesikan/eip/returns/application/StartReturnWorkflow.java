package io.github.nandydesikan.eip.returns.application;

import io.github.nandydesikan.eip.returns.application.command.StartReturnWorkflowCommand;
import io.github.nandydesikan.eip.returns.application.port.ClockPort;
import io.github.nandydesikan.eip.returns.application.port.IdentifierPort;
import io.github.nandydesikan.eip.returns.application.port.WorkflowRepository;
import io.github.nandydesikan.eip.returns.domain.ReturnWorkflow;
import org.springframework.stereotype.Service;

@Service
public class StartReturnWorkflow {

    private final WorkflowRepository workflowRepository;
    private final IdentifierPort identifierPort;
    private final ClockPort clockPort;

    public StartReturnWorkflow(
            WorkflowRepository workflowRepository,
            IdentifierPort identifierPort,
            ClockPort clockPort
    ) {
        this.workflowRepository = workflowRepository;
        this.identifierPort = identifierPort;
        this.clockPort = clockPort;
    }

    public WorkflowRepository.StartResult handle(StartReturnWorkflowCommand command) {
        var candidate = ReturnWorkflow.start(
                identifierPort.nextWorkflowId(),
                command.returnRequestId(),
                clockPort.now());
        return workflowRepository.saveIfAbsent(
                command.idempotencyKey(),
                RequestFingerprint.of(command),
                candidate);
    }
}
