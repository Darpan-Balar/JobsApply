package com.jobsapply.platform.support;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.SQLException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.springframework.dao.DataAccessException;

public final class DbAssertions {

    private DbAssertions() {}

    /** Asserts that the call fails because PostgreSQL rejected it citing {@code constraintOrMessage}. */
    public static void assertRejectedBy(ThrowingCallable call, String constraintOrMessage) {
        assertThatThrownBy(call)
                .isInstanceOf(DataAccessException.class)
                .satisfies(e -> {
                    Throwable root = ((DataAccessException) e).getMostSpecificCause();
                    org.assertj.core.api.Assertions.assertThat(root).isInstanceOf(SQLException.class);
                    org.assertj.core.api.Assertions.assertThat(root.getMessage()).contains(constraintOrMessage);
                });
    }
}
