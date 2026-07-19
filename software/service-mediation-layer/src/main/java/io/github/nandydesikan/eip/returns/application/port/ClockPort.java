package io.github.nandydesikan.eip.returns.application.port;

import java.time.Instant;

@FunctionalInterface
public interface ClockPort {
    Instant now();
}
