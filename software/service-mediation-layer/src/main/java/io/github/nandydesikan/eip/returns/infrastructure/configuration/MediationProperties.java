package io.github.nandydesikan.eip.returns.infrastructure.configuration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "mediation")
public record MediationProperties(
        @NotBlank String persistenceMode,
        @Positive int recoveryBatchSize,
        Duration recoveryLeaseDuration
) {
    public MediationProperties {
        if (recoveryLeaseDuration == null || recoveryLeaseDuration.isNegative() || recoveryLeaseDuration.isZero()) {
            throw new IllegalArgumentException("mediation.recovery-lease-duration must be positive");
        }
    }
}
