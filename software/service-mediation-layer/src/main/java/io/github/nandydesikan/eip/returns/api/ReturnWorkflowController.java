package io.github.nandydesikan.eip.returns.api;

import io.github.nandydesikan.eip.returns.api.request.CreateReturnRequest;
import io.github.nandydesikan.eip.returns.api.response.ReturnWorkflowResponse;
import io.github.nandydesikan.eip.returns.application.GetReturnWorkflow;
import io.github.nandydesikan.eip.returns.application.StartReturnWorkflow;
import io.github.nandydesikan.eip.returns.application.command.StartReturnWorkflowCommand;
import io.github.nandydesikan.eip.returns.domain.ReturnWorkflowId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/v1/returns")
public class ReturnWorkflowController {

    private final StartReturnWorkflow startReturnWorkflow;
    private final GetReturnWorkflow getReturnWorkflow;

    public ReturnWorkflowController(
            StartReturnWorkflow startReturnWorkflow,
            GetReturnWorkflow getReturnWorkflow
    ) {
        this.startReturnWorkflow = startReturnWorkflow;
        this.getReturnWorkflow = getReturnWorkflow;
    }

    @PostMapping
    public ResponseEntity<ReturnWorkflowResponse> create(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 200) String idempotencyKey,
            @Valid @RequestBody CreateReturnRequest request
    ) {
        var result = startReturnWorkflow.handle(new StartReturnWorkflowCommand(
                idempotencyKey,
                request.returnRequestId(),
                request.customerId(),
                request.orderId(),
                request.reason()));
        var location = URI.create("/api/v1/returns/" + result.workflow().id());
        return ResponseEntity.accepted()
                .location(location)
                .body(ReturnWorkflowResponse.from(result.workflow(), !result.created()));
    }

    @GetMapping("/{workflowId}")
    public ReturnWorkflowResponse get(@PathVariable UUID workflowId) {
        return ReturnWorkflowResponse.from(
                getReturnWorkflow.handle(new ReturnWorkflowId(workflowId)),
                false);
    }
}
