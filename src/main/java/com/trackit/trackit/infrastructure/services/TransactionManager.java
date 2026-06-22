package com.trackit.trackit.infrastructure.services;

import java.sql.Connection;
import java.sql.SQLException;

import com.trackit.trackit.application.ports.services.ITransactionManager;
import com.trackit.trackit.application.ports.services.TransactionAction;
import com.trackit.trackit.infrastructure.persistence.mysql.DbConnection;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TransactionManager implements ITransactionManager {

    @Override
    public void execute(TransactionAction action) {
        try (Connection conn = DbConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                action.run(conn);
                conn.commit();
            } catch (Exception e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackEx) {
                    // Log rollback failure
                    System.err.println("[Transaction] Failed to rollback connection: " + rollbackEx.getMessage());
                }
                if (e instanceof RuntimeException) {
                    throw (RuntimeException) e;
                }
                throw new RuntimeException("Transaction failed and was rolled back", e);
            } finally {
                try {
                    conn.setAutoCommit(true);
                } catch (SQLException e) {
                    throw new RuntimeException("Failed to set auto-commit", e);
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to establish database connection for transaction", e);
        }
    }
}
