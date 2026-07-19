package io.github.nandydesikan.eip.returns.infrastructure.configuration;

import io.github.nandydesikan.eip.returns.application.port.ClockPort;
import io.github.nandydesikan.eip.returns.application.port.IdentifierPort;
import io.github.nandydesikan.eip.returns.domain.ReturnWorkflowId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

@Configuration(proxyBeanMethods = false)
public class SystemPortsConfiguration {

    @Bean
    Clock systemClock() {
        return Clock.systemUTC();
    }

    @Bean
    ClockPort clockPort(Clock clock) {
        return () -> Instant.now(clock);
    }

    @Bean
    IdentifierPort identifierPort() {
        return () -> new ReturnWorkflowId(UUID.randomUUID());
    }
}
