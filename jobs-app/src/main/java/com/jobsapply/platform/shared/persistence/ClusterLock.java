package com.jobsapply.platform.shared.persistence;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Supplier;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.stereotype.Component;

/**
 * Single-flight across processes: a PostgreSQL session advisory lock, held on a dedicated connection for the duration
 * of the work. If another process (or a previous, still-running tick) holds the lock, the work is skipped, not queued.
 * The lock is released when the work ends, and by PostgreSQL if the process or connection dies.
 */
@Component
@RequiredArgsConstructor
public class ClusterLock {

    private final DataSource dataSource;

    /** @return the work's result, or {@code null} if the lock was held elsewhere and the work was skipped */
    public <T> T runExclusively(String name, Supplier<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            if (!call(connection, "select pg_try_advisory_lock(hashtextextended(?, 0))", name)) {
                return null;
            }
            try {
                return work.get();
            } finally {
                call(connection, "select pg_advisory_unlock(hashtextextended(?, 0))", name);
            }
        } catch (SQLException e) {
            throw new DataAccessResourceFailureException("advisory lock '" + name + "' failed", e);
        }
    }

    private static boolean call(Connection connection, String sql, String name) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }
}
