package io.github.nandydesikan.eip.returns.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateReturnRequest(
        @NotBlank @Size(max = 100) String returnRequestId,
        @NotBlank @Size(max = 100) String customerId,
        @NotBlank @Size(max = 100) String orderId,
        @NotBlank @Size(max = 500) String reason
) {
}
