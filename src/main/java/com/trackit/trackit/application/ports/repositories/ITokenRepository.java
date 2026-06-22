package com.trackit.trackit.application.ports.repositories;

import java.sql.Connection;
import java.util.Optional;

import com.trackit.trackit.core.domains.entities.user.Token;

public interface ITokenRepository {
    Token save(Token token);
    Token save(Connection conn, Token token);
    Optional<Token> findLatestByTargetAndPurpose(String targetId, String purpose);
    Optional<Token> findLatestByTargetAndPurpose(Connection conn, String targetId, String purpose);
    boolean delete(String id);
    boolean delete(Connection conn, String id);
    void deleteByTargetAndPurpose(String targetId, String purpose);
    void deleteByTargetAndPurpose(Connection conn, String targetId, String purpose);
}
