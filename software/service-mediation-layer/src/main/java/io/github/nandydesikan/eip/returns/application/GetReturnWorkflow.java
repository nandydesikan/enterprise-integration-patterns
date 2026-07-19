package io.github.nandydesikan.eip.returns.application;

import io.github.nandydesikan.eip.returns.application.port.WorkflowRepository;
import io.github.nandydesikan.eip.returns.domain.ReturnWorkflow;
import io.github.nandydesikan.eip.returns.domain.ReturnWorkflowId;
import org.springframework.stereotype.Service;

@Service
public class GetReturnWorkflow {

    private final WorkflowRepository workflowRepository;

    public GetReturnWorkflow(WorkflowRepository workflowRepository) {
        this.workflowRepository = workflowRepository;
    }

    public ReturnWorkflow handle(ReturnWorkflowId workflowId) {
        return workflowRepository.findById(workflowId)
                .orElseThrow(() -> new WorkflowNotFound(workflowId));
    }
}
