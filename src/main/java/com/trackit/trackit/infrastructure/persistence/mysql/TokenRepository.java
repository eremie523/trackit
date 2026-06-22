package com.trackit.trackit.infrastructure.persistence.mysql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.trackit.trackit.application.ports.repositories.ITokenRepository;
import com.trackit.trackit.core.domains.entities.user.Token;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class TokenRepository implements ITokenRepository {

    private static final List<String> columns = List.of(
        "id", "tokenHash", "targetId", "purpose", "expiresAt", "createdAt"
    );

    private final static String fullColString;

    static {
        List<String> aliased = new ArrayList<>();
        for (String col : columns) {
            aliased.add("`t`.`" + col + "` AS `" + col + "`");
        }
        fullColString = String.join(", ", aliased);
    }

    @Override
    public Token save(Token token) {
        try (Connection conn = DbConnection.getConnection()) {
            return this.save(conn, token);
        } catch (SQLException e) {
            throw new RuntimeException("save failed", e);
        }
    }

    @Override
    public Token save(Connection conn, Token token) {
        if (token.isNew) {
            return this.insert(conn, token);
        }
        throw new UnsupportedOperationException("Update operation for tokens is not supported");
    }

    @Override
    public Optional<Token> findLatestByTargetAndPurpose(String targetId, String purpose) {
        try (Connection conn = DbConnection.getConnection()) {
            return this.findLatestByTargetAndPurpose(conn, targetId, purpose);
        } catch (SQLException e) {
            throw new RuntimeException("findLatestByTargetAndPurpose failed", e);
        }
    }

    @Override
    public Optional<Token> findLatestByTargetAndPurpose(Connection conn, String targetId, String purpose) {
        String sql = String.format("""
            SELECT %s
            FROM `tokens` AS `t`
            WHERE `targetId` = ? AND `purpose` = ?
            ORDER BY `createdAt` DESC
            LIMIT 1
        """, fullColString);

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, targetId);
            stmt.setString(2, purpose);

            try (ResultSet result = stmt.executeQuery()) {
                if (result.next()) {
                    return Optional.of(toEntity(result));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("findLatestByTargetAndPurpose failed", e);
        }
    }

    @Override
    public boolean delete(String id) {
        try (Connection conn = DbConnection.getConnection()) {
            return this.delete(conn, id);
        } catch (SQLException e) {
            throw new RuntimeException("delete failed", e);
        }
    }

    @Override
    public boolean delete(Connection conn, String id) {
        String sql = "DELETE FROM `tokens` WHERE `id` = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            throw new RuntimeException("delete failed", e);
        }
    }

    @Override
    public void deleteByTargetAndPurpose(String targetId, String purpose) {
        try (Connection conn = DbConnection.getConnection()) {
            this.deleteByTargetAndPurpose(conn, targetId, purpose);
        } catch (SQLException e) {
            throw new RuntimeException("deleteByTargetAndPurpose failed", e);
        }
    }

    @Override
    public void deleteByTargetAndPurpose(Connection conn, String targetId, String purpose) {
        String sql = "DELETE FROM `tokens` WHERE `targetId` = ? AND `purpose` = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, targetId);
            stmt.setString(2, purpose);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new RuntimeException("deleteByTargetAndPurpose failed", e);
        }
    }

    private Token insert(Connection conn, Token token) {
        String sql = """
            INSERT INTO `tokens` (`id`, `tokenHash`, `targetId`, `purpose`, `expiresAt`, `createdAt`)
            VALUES (?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, token.id);
            stmt.setString(2, token.tokenHash);
            stmt.setString(3, token.targetId);
            stmt.setString(4, token.purpose);
            stmt.setTimestamp(5, Timestamp.from(token.expiresAt));
            stmt.setTimestamp(6, Timestamp.from(token.createdAt));

            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new RuntimeException("Insert failed, no rows affected");
            }

            return token;
        } catch (SQLException e) {
            throw new RuntimeException("insert failed", e);
        }
    }

    private Token toEntity(ResultSet result) throws SQLException {
        String id = result.getString("id");
        String tokenHash = result.getString("tokenHash");
        String targetId = result.getString("targetId");
        String purpose = result.getString("purpose");
        Timestamp expiresAtTs = result.getTimestamp("expiresAt");
        Timestamp createdAtTs = result.getTimestamp("createdAt");

        Instant expiresAt = expiresAtTs != null ? expiresAtTs.toInstant() : null;
        Instant createdAt = createdAtTs != null ? createdAtTs.toInstant() : null;

        return Token.reconstruct(id, tokenHash, targetId, purpose, expiresAt, createdAt);
    }
}
