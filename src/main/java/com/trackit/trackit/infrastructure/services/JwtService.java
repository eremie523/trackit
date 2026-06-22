package com.trackit.trackit.infrastructure.services;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.trackit.trackit.application.ports.services.IJwtService;
import com.trackit.trackit.core.domains.entities.user.User;
import com.trackit.trackit.infrastructure.config.Env;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Date;

@ApplicationScoped
public class JwtService implements IJwtService {

    private final String secret;
    private final long expirationMs;

    public JwtService() {
        this.secret = Env.get("JWT_SECRET", "super_secret_jwt_key_for_trackit_app_development_only_12345");
        
        long exp;
        try {
            exp = Long.parseLong(Env.get("JWT_EXPIRATION_MS", "86400000"));
        } catch (NumberFormatException e) {
            exp = 86400000; // 24 hours
        }
        this.expirationMs = exp;
    }

    @Override
    public String generateToken(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null for token generation");
        }
        
        Algorithm algorithm = Algorithm.HMAC256(secret);
        return JWT.create()
                .withSubject(user.id)
                .withClaim("email", user.email)
                .withClaim("role", user.role.name())
                .withClaim("fullName", user.fullName)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + expirationMs))
                .sign(algorithm);
    }

    @Override
    public java.util.Optional<com.trackit.trackit.application.dto.DecodedToken> decodeToken(String token) {
        if (token == null || token.isEmpty()) {
            return java.util.Optional.empty();
        }

        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            com.auth0.jwt.interfaces.DecodedJWT decoded = JWT.require(algorithm)
                    .build()
                    .verify(token);

            String userId = decoded.getSubject();
            String email = decoded.getClaim("email").asString();
            String role = decoded.getClaim("role").asString();

            return java.util.Optional.of(new com.trackit.trackit.application.dto.DecodedToken(userId, email, role));
        } catch (Exception e) {
            System.err.println("[JwtService] Token verification failed: " + e.getMessage());
            return java.util.Optional.empty();
        }
    }
}
