package io.github.nandydesikan.eip.returns.application;

import io.github.nandydesikan.eip.returns.application.command.StartReturnWorkflowCommand;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class RequestFingerprint {

    private RequestFingerprint() {
    }

    static String of(StartReturnWorkflowCommand command) {
        var canonical = String.join("\u001f",
                normalize(command.returnRequestId()),
                normalize(command.customerId()),
                normalize(command.orderId()),
                normalize(command.reason()));
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 must be available in every Java runtime", exception);
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }
}
