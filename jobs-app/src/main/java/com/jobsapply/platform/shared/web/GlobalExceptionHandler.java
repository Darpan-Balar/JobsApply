package com.jobsapply.platform.shared.web;

import com.jobsapply.platform.shared.error.DomainRuleViolationException;
import com.jobsapply.platform.shared.error.InvalidStateTransitionException;
import com.jobsapply.platform.shared.error.MissingUserContextException;
import com.jobsapply.platform.shared.error.NotFoundException;
import com.jobsapply.platform.shared.error.OwnershipViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain exceptions to RFC 9457 problem details. Database error details are logged, never returned (they may
 * contain data values).
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

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

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
