package com.jobsapply.platform.shared.web;

import com.jobsapply.platform.shared.error.ConcurrencyConflictException;
import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.shared.error.InvalidStateTransitionException;
import com.jobsapply.platform.shared.error.MissingUserContextException;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.error.OwnershipViolationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.Comparator;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps exceptions to RFC 9457 problem details. Standard Spring MVC errors are handled by the base class. Validation
 * problems carry an {@code errors} list of {@code {field, message}}. Database error details are logged, never returned
 * (they may contain data values).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    public record FieldProblem(String field, String message) {}

    @ExceptionHandler({NotFoundException.class, OwnershipViolationException.class})
    ProblemDetail notFound(RuntimeException ex) {
        // Ownership violations are reported as "not found" so that other users' records are not revealed.
        String detail = ex instanceof NotFoundException ? ex.getMessage() : "Resource not found";
        return problem(HttpStatus.NOT_FOUND, "Not found", detail);
    }

    @ExceptionHandler(DomainRuleViolationException.class)
    ProblemDetail ruleViolation(DomainRuleViolationException ex) {
        return problem(HttpStatus.CONFLICT, "Business rule violated", ex.getMessage());
    }

    @ExceptionHandler(InvalidStateTransitionException.class)
    ProblemDetail invalidTransition(InvalidStateTransitionException ex) {
        return problem(HttpStatus.CONFLICT, "Invalid state transition", ex.getMessage());
    }

    @ExceptionHandler(ConcurrencyConflictException.class)
    ProblemDetail staleVersion(ConcurrencyConflictException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification", ex.getMessage());
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail concurrentModification(OptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent modification", "The resource was modified concurrently; reload and retry");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ProblemDetail integrityViolation(DataIntegrityViolationException ex) {
        log.warn("Data integrity violation: {}", ex.getMostSpecificCause().getClass().getSimpleName());
        return problem(HttpStatus.CONFLICT, "Conflict", "The request conflicts with existing data");
    }

    @ExceptionHandler(MissingUserContextException.class)
    ProblemDetail missingUser(MissingUserContextException ex) {
        return problem(HttpStatus.UNAUTHORIZED, "Unauthenticated", "Authentication is required");
    }

    /** Service-level Bean Validation ({@code @Validated} services). */
    @ExceptionHandler(ConstraintViolationException.class)
    ProblemDetail constraintViolation(ConstraintViolationException ex) {
        List<FieldProblem> errors = ex.getConstraintViolations().stream()
                .map(v -> new FieldProblem(fieldOf(v), v.getMessage()))
                .sorted(Comparator.comparing(FieldProblem::field))
                .toList();
        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request", "One or more fields are invalid");
        problem.setProperty("errors", errors);
        return problem;
    }

    /** Request-body Bean Validation ({@code @Valid @RequestBody}). */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem = ex.getBody();
        problem.setTitle("Invalid request");
        problem.setProperty("errors", ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldProblem(e.getField(), e.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldProblem::field))
                .toList());
        return handleExceptionInternal(ex, problem, headers, status, request);
    }

    /** {@code create.spec.website} → {@code website}: drop the method and parameter nodes. */
    private static String fieldOf(ConstraintViolation<?> violation) {
        StringBuilder field = new StringBuilder();
        int index = 0;
        for (Path.Node node : violation.getPropertyPath()) {
            if (index++ < 2) {
                continue;
            }
            if (node.isInIterable()) {
                field.append('[').append(node.getIndex() != null ? node.getIndex() : node.getKey()).append(']');
            }
            if (node.getName() != null && !node.getName().startsWith("<")) {
                field.append(field.isEmpty() ? "" : ".").append(node.getName());
            }
        }
        return field.isEmpty() ? violation.getPropertyPath().toString() : field.toString();
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
