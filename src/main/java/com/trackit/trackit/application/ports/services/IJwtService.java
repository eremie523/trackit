package com.trackit.trackit.application.ports.services;

import java.util.Optional;
import com.trackit.trackit.application.dto.DecodedToken;
import com.trackit.trackit.core.domains.entities.user.User;

public interface IJwtService {
    String generateToken(User user);
    Optional<DecodedToken> decodeToken(String token);
}
