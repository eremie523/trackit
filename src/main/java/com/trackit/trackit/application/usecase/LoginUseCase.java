package com.trackit.trackit.application.usecase;

import com.trackit.trackit.application.dto.LoginInputDTO;
import com.trackit.trackit.application.dto.LoginResponseDTO;
import com.trackit.trackit.application.ports.repositories.IUserRepository;
import com.trackit.trackit.application.ports.services.ICryptographicService;
import com.trackit.trackit.application.ports.services.IJwtService;
import com.trackit.trackit.core.domains.entities.user.User;
import com.trackit.trackit.core.domains.entities.user.valueObjects.UserStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;

@ApplicationScoped
public class LoginUseCase implements IUseCase<LoginInputDTO, LoginResponseDTO> {

    private final IUserRepository userRepository;
    private final ICryptographicService cryptographicService;
    private final IJwtService jwtService;

    @Inject
    public LoginUseCase(IUserRepository userRepository,
            ICryptographicService cryptographicService,
            IJwtService jwtService) {
        this.userRepository = userRepository;
        this.cryptographicService = cryptographicService;
        this.jwtService = jwtService;
    }

    // Client proxy no-arg constructor required by CDI specifications
    protected LoginUseCase() {
        this.userRepository = null;
        this.cryptographicService = null;
        this.jwtService = null;
    }

    @Override
    public Optional<LoginResponseDTO> execute(LoginInputDTO input) {
        if (input == null || input.email() == null || input.password() == null) {
            return Optional.empty();
        }

        Optional<User> userOpt = userRepository.findByEmail(input.email().trim());
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }

        User user = userOpt.get();

        // User must be ACTIVE to authenticate successfully
        if (user.status != UserStatus.ACTIVE) {
            return Optional.empty();
        }

        boolean isValidPassword = cryptographicService.verifyPassword(input.password(), user.passwordHash);
        if (!isValidPassword) {
            return Optional.empty();
        }

        // Generate JWT token
        String token = jwtService.generateToken(user);

        return Optional.of(new LoginResponseDTO(
                token,
                user.role.name(),
                user.email,
                user.fullName));
    }
}
