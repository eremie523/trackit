package com.trackit.trackit.application.ports.repositories;

import java.util.List;
import java.util.Optional;

import com.trackit.trackit.core.domains.entities.user.Doctor;

public interface IDoctorRepository {
    public Doctor save(Doctor user);
    public Optional<Doctor> findByUserId(String id);
    public List<Doctor> findMany();
    public boolean delete(String id);
}
