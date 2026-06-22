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

import com.trackit.trackit.application.ports.repositories.IPatientRepository;
import com.trackit.trackit.core.domains.entities.user.Patient;
import com.trackit.trackit.core.domains.entities.user.dto.ReconstructPatientDTO;
import com.trackit.trackit.core.domains.entities.user.valueObjects.Address;
import com.trackit.trackit.core.domains.entities.user.valueObjects.BloodType;
import com.trackit.trackit.core.domains.entities.user.valueObjects.Genotype;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PatientRepository implements IPatientRepository {

    private static final List<String> columns = List.of(
        "id", "userId", "isMarried", "bloodType", "genotype", "dob", "age",
        "addressCountry", "addressCity", "addressState", "addressStreetAddress", "addressZipCode"
    );

    private final static String fullColString;

    static {
        List<String> aliased = new ArrayList<>();
        for (String col : columns) {
            aliased.add("`p`.`" + col + "` AS `" + col + "`");
        }
        fullColString = String.join(", ", aliased);
    }

    @Override
    public Patient save(Patient patient) {
        try (Connection conn = DbConnection.getConnection()) {
            return this.save(conn, patient);
        } catch (SQLException e) {
            throw new RuntimeException("save failed", e);
        }
    }

    @Override
    public Patient save(Connection conn, Patient patient) {
        if (patient.isNew) {
            return this.insert(conn, patient);
        }
        return this.update(conn, patient);
    }

    @Override
    public Optional<Patient> findByUserId(String userId) {
        try (Connection conn = DbConnection.getConnection()) {
            return this.findByUserId(conn, userId);
        } catch (SQLException e) {
            throw new RuntimeException("findByUserId failed for userId=" + userId, e);
        }
    }

    @Override
    public Optional<Patient> findByUserId(Connection conn, String userId) {
        String sql = String.format("""
            SELECT %s
            FROM `patients` AS `p`
            WHERE `userId` = ?
        """, fullColString);

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, userId);

            try (ResultSet result = stmt.executeQuery()) {
                if (result.next()) {
                    return Optional.of(toEntity(result));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("findByUserId failed for userId=" + userId, e);
        }
    }

    @Override
    public List<Patient> findMany() {
        try (Connection conn = DbConnection.getConnection()) {
            return this.findMany(conn);
        } catch (SQLException e) {
            throw new RuntimeException("findMany failed", e);
        }
    }

    @Override
    public List<Patient> findMany(Connection conn) {
        String sql = String.format("""
            SELECT %s
            FROM `patients` AS `p`
        """, fullColString);

        List<Patient> patients = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet result = stmt.executeQuery()) {
            while (result.next()) {
                patients.add(toEntity(result));
            }
            return patients;
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
        String sql = "DELETE FROM `patients` WHERE `id` = ?";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, id);
            int rows = stmt.executeUpdate();
            return rows > 0;
        } catch (SQLException e) {
            throw new RuntimeException("delete failed for id=" + id, e);
        }
    }

    private Patient insert(Connection conn, Patient patient) {
        String sql = """
            INSERT INTO `patients` (
                `id`, `userId`, `isMarried`, `bloodType`, `genotype`, `dob`, `age`,
                `addressCountry`, `addressCity`, `addressState`, `addressStreetAddress`, `addressZipCode`
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, patient.id);
            stmt.setString(2, patient.userId);
            stmt.setBoolean(3, patient.isMarried);
            stmt.setString(4, patient.bloodType != null ? patient.bloodType.name() : null);
            stmt.setString(5, patient.genotype != null ? patient.genotype.name() : null);
            stmt.setTimestamp(6, Timestamp.from(patient.dob));
            stmt.setInt(7, patient.age);
            stmt.setString(8, patient.address != null ? patient.address.country : null);
            stmt.setString(9, patient.address != null ? patient.address.city : null);
            stmt.setString(10, patient.address != null ? patient.address.state : null);
            stmt.setString(11, patient.address != null ? patient.address.streetAddress : null);
            stmt.setString(12, patient.address != null ? patient.address.zipCode : null);

            int rows = stmt.executeUpdate();
            if (rows == 0) {
                throw new RuntimeException("Insert failed, no rows affected");
            }

            return this.findByUserId(conn, patient.userId)
                .orElseThrow(() -> new RuntimeException("Patient not found after insert"));
        } catch (SQLException e) {
            throw new RuntimeException("insert failed", e);
        }
    }

    private Patient update(Connection conn, Patient patient) {
        String sql = """
            UPDATE `patients`
            SET
                `isMarried` = ?,
                `bloodType` = ?,
                `genotype` = ?,
                `age` = ?,
                `addressCountry` = ?,
                `addressCity` = ?,
                `addressState` = ?,
                `addressStreetAddress` = ?,
                `addressZipCode` = ?
            WHERE `id` = ?
        """;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, patient.isMarried);
            stmt.setString(2, patient.bloodType != null ? patient.bloodType.name() : null);
            stmt.setString(3, patient.genotype != null ? patient.genotype.name() : null);
            stmt.setInt(4, patient.age);
            stmt.setString(5, patient.address != null ? patient.address.country : null);
            stmt.setString(6, patient.address != null ? patient.address.city : null);
            stmt.setString(7, patient.address != null ? patient.address.state : null);
            stmt.setString(8, patient.address != null ? patient.address.streetAddress : null);
            stmt.setString(9, patient.address != null ? patient.address.zipCode : null);
            stmt.setString(10, patient.id);

            stmt.executeUpdate();

            return this.findByUserId(conn, patient.userId)
                .orElseThrow(() -> new RuntimeException("Updated record not found"));
        } catch (SQLException e) {
            throw new RuntimeException("update failed", e);
        }
    }

    private Patient toEntity(ResultSet result) throws SQLException {
        String id = result.getString("id");
        String userId = result.getString("userId");
        boolean isMarried = result.getBoolean("isMarried");
        
        String bloodTypeStr = result.getString("bloodType");
        BloodType bloodType = bloodTypeStr != null ? BloodType.valueOf(bloodTypeStr) : null;
        
        String genotypeStr = result.getString("genotype");
        Genotype genotype = genotypeStr != null ? Genotype.valueOf(genotypeStr) : null;
        
        Timestamp dobTs = result.getTimestamp("dob");
        Instant dob = dobTs != null ? dobTs.toInstant() : null;
        
        int age = result.getInt("age");
        
        Address address = new Address(
            result.getString("addressCountry"),
            result.getString("addressCity"),
            result.getString("addressState"),
            result.getString("addressStreetAddress"),
            result.getString("addressZipCode")
        );

        return Patient.reconstruct(new ReconstructPatientDTO(
            id, userId, isMarried, bloodType, genotype, dob, age, address
        ));
    }

    @Override
    public List<com.trackit.trackit.application.dto.PatientSearchItemDTO> fetchAll(com.trackit.trackit.application.dto.SearchPatientFilterDTO filter) {
        try (Connection conn = DbConnection.getConnection()) {
            return this.fetchAll(conn, filter);
        } catch (SQLException e) {
            throw new RuntimeException("fetchAll failed", e);
        }
    }

    @Override
    public List<com.trackit.trackit.application.dto.PatientSearchItemDTO> fetchAll(Connection conn, com.trackit.trackit.application.dto.SearchPatientFilterDTO filter) {
        StringBuilder sql = new StringBuilder("""
            SELECT 
              `p`.`id` AS `patientId`,
              `u`.`id` AS `userId`,
              `u`.`fullName` AS `fullName`,
              `u`.`nationalID` AS `nationalID`,
              `p`.`dob` AS `dob`,
              `u`.`gender` AS `gender`,
              `u`.`phoneNumber` AS `phoneNumber`
            FROM `users` AS `u`
            JOIN `patients` AS `p` ON `u`.`id` = `p`.`userId`
            WHERE 1=1
        """);

        List<Object> params = new ArrayList<>();

        if (filter != null) {
            if (filter.firstName() != null && !filter.firstName().trim().isEmpty()) {
                sql.append(" AND `u`.`fullName` LIKE ?");
                params.add("%" + filter.firstName().trim() + "%");
            }
            if (filter.lastName() != null && !filter.lastName().trim().isEmpty()) {
                sql.append(" AND `u`.`fullName` LIKE ?");
                params.add("%" + filter.lastName().trim() + "%");
            }
            if (filter.nationalID() != null && !filter.nationalID().trim().isEmpty()) {
                sql.append(" AND `u`.`nationalID` LIKE ?");
                params.add("%" + filter.nationalID().trim() + "%");
            }
            if (filter.phoneNumber() != null && !filter.phoneNumber().trim().isEmpty()) {
                sql.append(" AND `u`.`phoneNumber` LIKE ?");
                params.add("%" + filter.phoneNumber().trim() + "%");
            }
            if (filter.gender() != null && !filter.gender().trim().isEmpty()) {
                sql.append(" AND `u`.`gender` = ?");
                params.add(filter.gender().trim().toUpperCase());
            }
            if (filter.dob() != null && !filter.dob().trim().isEmpty()) {
                String dateStr = filter.dob().trim();
                // Standardize MM/DD/YYYY to YYYY-MM-DD for SQL compatibility
                if (dateStr.contains("/")) {
                    String[] parts = dateStr.split("/");
                    if (parts.length == 3) {
                        dateStr = parts[2] + "-" + parts[0] + "-" + parts[1];
                    }
                }
                sql.append(" AND DATE(`p`.`dob`) = ?");
                params.add(dateStr);
            }
        }

        if (params.isEmpty()) {
            sql.append(" ORDER BY `p`.`createdAt` DESC LIMIT 25");
        } else {
            sql.append(" ORDER BY `p`.`createdAt` DESC");
        }

        List<com.trackit.trackit.application.dto.PatientSearchItemDTO> list = new ArrayList<>();
        try (PreparedStatement stmt = conn.prepareStatement(sql.toString())) {
            for (int i = 0; i < params.size(); i++) {
                stmt.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Timestamp dobTs = rs.getTimestamp("dob");
                    Instant dobInstant = dobTs != null ? dobTs.toInstant() : null;

                    list.add(new com.trackit.trackit.application.dto.PatientSearchItemDTO(
                        rs.getString("patientId"),
                        rs.getString("userId"),
                        rs.getString("fullName"),
                        rs.getString("nationalID"),
                        dobInstant,
                        rs.getString("gender"),
                        rs.getString("phoneNumber")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("fetchAll database error", e);
        }

        return list;
    }
}
