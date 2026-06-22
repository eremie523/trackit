package com.trackit.trackit.application.ports.repositories;

import java.util.List;
import java.util.Optional;

import com.trackit.trackit.core.domains.entities.user.Patient;

public interface IPatientRepository {
    public Patient save(Patient user);
    public Patient save(java.sql.Connection conn, Patient patient);
    public Optional<Patient> findByUserId(String id);
    public Optional<Patient> findByUserId(java.sql.Connection conn, String id);
    public List<Patient> findMany();
    public List<Patient> findMany(java.sql.Connection conn);
    public boolean delete(String id);
    public boolean delete(java.sql.Connection conn, String id);
    public List<com.trackit.trackit.application.dto.PatientSearchItemDTO> fetchAll(com.trackit.trackit.application.dto.SearchPatientFilterDTO filter);
    public List<com.trackit.trackit.application.dto.PatientSearchItemDTO> fetchAll(java.sql.Connection conn, com.trackit.trackit.application.dto.SearchPatientFilterDTO filter);
}