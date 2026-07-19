package io.github.nandydesikan.eip.returns.api;

import io.github.nandydesikan.eip.returns.application.IdempotencyConflict;
import io.github.nandydesikan.eip.returns.application.WorkflowNotFound;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(IdempotencyConflict.class)
    ProblemDetail idempotencyConflict(IdempotencyConflict exception) {
        return problem(HttpStatus.CONFLICT, "Idempotency conflict", exception.getMessage());
    }

    @ExceptionHandler(WorkflowNotFound.class)
    ProblemDetail workflowNotFound(WorkflowNotFound exception) {
        return problem(HttpStatus.NOT_FOUND, "Workflow not found", exception.getMessage());
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, IllegalArgumentException.class})
    ProblemDetail invalidRequest(Exception exception) {
        return problem(HttpStatus.BAD_REQUEST, "Invalid request", exception.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        var problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create("urn:problem:" + title.toLowerCase().replace(' ', '-')));
        return problem;
    }
}
