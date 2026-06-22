package com.trackit.trackit.infrastructure.services;

import java.security.SecureRandom;
import java.sql.Connection;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import com.trackit.trackit.application.ports.repositories.ITokenRepository;
import com.trackit.trackit.application.ports.services.ICryptographicService;
import com.trackit.trackit.application.ports.services.ITokenService;
import com.trackit.trackit.core.domains.entities.user.Token;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TokenService implements ITokenService {

    private final ITokenRepository tokenRepository;
    private final ICryptographicService cryptographicService;
    private final SecureRandom secureRandom = new SecureRandom();

    @Inject
    public TokenService(ITokenRepository tokenRepository, ICryptographicService cryptographicService) {
        this.tokenRepository = tokenRepository;
        this.cryptographicService = cryptographicService;
    }

    protected TokenService() {
        this.tokenRepository = null;
        this.cryptographicService = null;
    }

    @Override
    public String generateAndSaveOTP(String targetId, String purpose) {
        String plainOtp = String.format("%06d", secureRandom.nextInt(1000000));
        String tokenHash = cryptographicService.hashPassword(plainOtp);
        Instant expiresAt = Instant.now().plus(3, ChronoUnit.DAYS);

        tokenRepository.deleteByTargetAndPurpose(targetId, purpose);

        Token token = new Token(tokenHash, targetId, purpose, expiresAt);
        tokenRepository.save(token);

        return plainOtp;
    }

    @Override
    public String generateAndSaveOTP(Connection conn, String targetId, String purpose) {
        String plainOtp = String.format("%06d", secureRandom.nextInt(1000000));
        String tokenHash = cryptographicService.hashPassword(plainOtp);
        Instant expiresAt = Instant.now().plus(3, ChronoUnit.DAYS);

        tokenRepository.deleteByTargetAndPurpose(conn, targetId, purpose);

        Token token = new Token(tokenHash, targetId, purpose, expiresAt);
        tokenRepository.save(conn, token);

        return plainOtp;
    }

    @Override
    public boolean verifyOTP(String targetId, String plainOtp, String purpose) {
        Optional<Token> tokenOpt = tokenRepository.findLatestByTargetAndPurpose(targetId, purpose);
        if (tokenOpt.isEmpty()) {
            return false;
        }

        Token token = tokenOpt.get();

        if (token.expiresAt.isBefore(Instant.now())) {
            return false;
        }

        return cryptographicService.verifyPassword(plainOtp, token.tokenHash);
    }

    @Override
    public boolean verifyOTP(Connection conn, String targetId, String plainOtp, String purpose) {
        Optional<Token> tokenOpt = tokenRepository.findLatestByTargetAndPurpose(conn, targetId, purpose);
        if (tokenOpt.isEmpty()) {
            return false;
        }

        Token token = tokenOpt.get();

        if (token.expiresAt.isBefore(Instant.now())) {
            return false;
        }

        return cryptographicService.verifyPassword(plainOtp, token.tokenHash);
    }

    @Override
    public void invalidateOTP(String targetId, String purpose) {
        tokenRepository.deleteByTargetAndPurpose(targetId, purpose);
    }

    @Override
    public void invalidateOTP(Connection conn, String targetId, String purpose) {
        tokenRepository.deleteByTargetAndPurpose(conn, targetId, purpose);
    }
}
