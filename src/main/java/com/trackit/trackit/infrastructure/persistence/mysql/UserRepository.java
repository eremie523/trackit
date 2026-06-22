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

import com.trackit.trackit.application.ports.repositories.IUserRepository;
import com.trackit.trackit.core.domains.entities.user.User;
import com.trackit.trackit.core.domains.entities.user.dto.ReconstructUserDTO;
import com.trackit.trackit.core.domains.entities.user.valueObjects.Gender;
import com.trackit.trackit.core.domains.entities.user.valueObjects.UserRole;
import com.trackit.trackit.core.domains.entities.user.valueObjects.UserStatus;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class UserRepository implements IUserRepository {
    private static final List<String> columns = List.of(
        "id", "email", "passwordHash", "fullName", "role", "status", "nationalID", "gender", "phoneNumber", "createdAt", "updatedAt"
    );

    private final static String fullColString;

    static {
        if (columns.isEmpty()) {
            fullColString = "*";
        } else {
            List<String> aliased = new ArrayList<>();
            for (String col : columns) {
                aliased.add("`u`.`" + col + "` AS `" + col + "`");
            }
            fullColString = String.join(", ", aliased);
        }
    }

    @Override
    public User save(User user) {
        try (Connection conn = DbConnection.getConnection()) {
            return this.save(conn, user);
        } catch (SQLException e) {
            throw new RuntimeException("save failed", e);
        }
    }

    @Override
    public User save(Connection conn, User user) {
        if (user.isNew) {
            return this.insert(conn, user);
        }
        return this.update(conn, user);
    }

    @Override
    public Optional<User> findById(String id) {
        try (Connection conn = DbConnection.getConnection()) {
            return this.findById(conn, id);
        } catch (SQLException e) {
            throw new RuntimeException("findById failed for id=" + id, e);
        }
    }

    @Override
    public Optional<User> findById(Connection conn, String id) {
        String sql = String.format("""
            SELECT %s
            FROM `users` AS `u`
            WHERE `id` = ? 
        """, UserRepository.fullColString);

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);

            try (ResultSet result = stmt.executeQuery()) {
                if (result.next()) return Optional.of(toEntity(result));
                return Optional.empty();
            }
        } catch (SQLException sqlError) {
            throw new RuntimeException("findById failed for id=" + id, sqlError);
        } 
    }

    @Override
    public Optional<User> findByEmail(String email) {
        try (Connection conn = DbConnection.getConnection()) {
            return this.findByEmail(conn, email);
        } catch (SQLException e) {
            throw new RuntimeException("findByEmail failed for email=" + email, e);
        }
    }

    @Override
    public Optional<User> findByEmail(Connection conn, String email) {
        String sql = String.format("""
            SELECT %s
            FROM `users` AS `u`
            WHERE `email` = ? 
        """, UserRepository.fullColString);

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, email);

            try (ResultSet result = stmt.executeQuery()) {
                if (result.next()) return Optional.of(toEntity(result));
                return Optional.empty();
            }
        } catch (SQLException sqlError) {
            throw new RuntimeException("findByEmail failed for email=" + email, sqlError);
        }
    }

    @Override
    public List<User> findMany() {
        try (Connection conn = DbConnection.getConnection()) {
            return this.findMany(conn);
        } catch (SQLException e) {
            throw new RuntimeException("findMany failed", e);
        }
    }

    @Override
    public List<User> findMany(Connection conn) {
        String sql = String.format("""
                SELECT %s
                FROM `users` AS `u`
        """, UserRepository.fullColString);

        List<User> users = new ArrayList<User>();
        
        try (PreparedStatement stmt = conn.prepareStatement(sql); ResultSet result = stmt.executeQuery()) {
            while (result.next()) {
                users.add(this.toEntity(result));
            }

            return users;
        } catch (SQLException e) {
            throw new RuntimeException("findMany failed", e);
        }
    }

    @Override
    public boolean delete(String id) {
        try (Connection conn = DbConnection.getConnection()) {
            return this.delete(conn, id);
        } catch (SQLException e) {
            throw new RuntimeException("delete failed for id=" + id, e);
        }
    }

    @Override
    public boolean delete(Connection conn, String id) {
        String sql = "DELETE FROM `users` WHERE `id` = ?";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            int rows = stmt.executeUpdate();

            return rows > 0;
        } catch (SQLException e) {
            throw new RuntimeException("delete failed for id=" + id, e);
        }
    }

    private User insert(Connection conn, User user) {
        String sql = "INSERT INTO `users` (`id`, `email`, `passwordHash`, `fullName`, `role`, `status`, `nationalID`, `gender`, `phoneNumber`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.id);
            stmt.setString(2, user.email);
            stmt.setString(3, user.passwordHash);
            stmt.setString(4, user.fullName);
            stmt.setString(5, user.role.toString());
            stmt.setString(6, user.status.toString());
            stmt.setString(7, user.nationalID);
            stmt.setString(8, user.gender != null ? user.gender.toString() : null);
            stmt.setString(9, user.phoneNumber);

            int rows = stmt.executeUpdate();
            if (rows == 0) throw new RuntimeException("Insert failed, no rows affected");

            User newUser = this.findById(conn, user.id)
            .orElseThrow(() -> new RuntimeException("User not found after insert"));

            return newUser; 
        } catch (SQLException e) {
            throw new RuntimeException("insert failed", e);
        }
    }

    private User update(Connection conn, User user) {
        String sql = """
                    UPDATE `users`
                    SET 
                        `passwordHash` = ?,
                        `fullName` = ?,
                        `role` = ?,
                        `status` = ?,
                        `phoneNumber` = ?
                    WHERE `id` = ?
                """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, user.passwordHash);
            stmt.setString(2, user.fullName);
            stmt.setString(3, user.role.toString());
            stmt.setString(4, user.status.toString());
            stmt.setString(5, user.phoneNumber);
            stmt.setString(6, user.id);

            stmt.executeUpdate();
            
            User updatedUser = this.findById(conn, user.id)
            .orElseThrow(() -> new RuntimeException("Updated record not found"));

            return updatedUser;
        } catch (Exception e) {
            throw new RuntimeException("update failed", e);
        }
    }

    private User toEntity(ResultSet result) throws SQLException {
        String id = result.getString("id");
        String email = result.getString("email");
        String passwordHash = result.getString("passwordHash");
        String fullName = result.getString("fullName");
        UserRole role = UserRole.valueOf(result.getString("role"));
        UserStatus status = UserStatus.valueOf(result.getString("status"));
        String nationalID = result.getString("nationalID");
        Gender gender = Gender.valueOf(result.getString("gender"));
        String phoneNumber = result.getString("phoneNumber");
        
        Timestamp createdAtTs = result.getTimestamp("createdAt");
        Timestamp updatedAtTs = result.getTimestamp("updatedAt");

        Instant createdAt = createdAtTs != null ? createdAtTs.toInstant() : null;
        Instant updatedAt = updatedAtTs != null ? updatedAtTs.toInstant() : null;

        return User.reconstruct(new ReconstructUserDTO(
            id,
            email,
            passwordHash,
            fullName,
            role,
            status,
            nationalID,
            gender,
            phoneNumber,
            createdAt,
            updatedAt
        ));
    }
}
