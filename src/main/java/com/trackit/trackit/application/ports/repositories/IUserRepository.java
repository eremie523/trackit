package com.trackit.trackit.application.ports.repositories;

import java.util.List;
import java.util.Optional;

import com.trackit.trackit.core.domains.entities.user.User;

public interface IUserRepository {
    public User save(User user);
    public User save(java.sql.Connection conn, User user);
    public Optional<User> findById(String id);
    public Optional<User> findById(java.sql.Connection conn, String id);
    public Optional<User> findByEmail(String email);
    public Optional<User> findByEmail(java.sql.Connection conn, String email);
    public List<User> findMany();
    public List<User> findMany(java.sql.Connection conn);
    public boolean delete(String id);
    public boolean delete(java.sql.Connection conn, String id);
}
