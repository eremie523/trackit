package com.trackit.trackit.application.usecase;

import java.util.Optional;

import com.trackit.trackit.application.dto.OnboardPatientInputDTO;
import com.trackit.trackit.application.dto.OnboardPatientResponseDTO;
import com.trackit.trackit.application.ports.repositories.IUserRepository;
import com.trackit.trackit.application.ports.services.ICryptographicService;
import com.trackit.trackit.application.ports.services.ITokenService;
import com.trackit.trackit.application.ports.services.ITransactionManager;
import com.trackit.trackit.core.domains.entities.user.User;
import com.trackit.trackit.core.domains.entities.user.valueObjects.UserStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class OnboardPatientUseCase implements IUseCase<OnboardPatientInputDTO, OnboardPatientResponseDTO> {

    private final IUserRepository userRepository;
    private final ITokenService tokenService;
    private final ICryptographicService cryptographicService;
    private final ITransactionManager transactionManager;

    @Inject
    public OnboardPatientUseCase(IUserRepository userRepository,
            ITokenService tokenService,
            ICryptographicService cryptographicService,
            ITransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.tokenService = tokenService;
        this.cryptographicService = cryptographicService;
        this.transactionManager = transactionManager;
    }

    protected OnboardPatientUseCase() {
        this.userRepository = null;
        this.tokenService = null;
        this.cryptographicService = null;
        this.transactionManager = null;
    }

    @Override
    public Optional<OnboardPatientResponseDTO> execute(OnboardPatientInputDTO input) {
        if (input == null || input.email() == null || input.password() == null || input.otp() == null) {
            return Optional.empty();
        }

        // 1. Fetch user by email
        User user = userRepository.findByEmail(input.email().trim())
                .orElseThrow(() -> new IllegalArgumentException("User with this email does not exist."));

        // 2. Enforce check that user status must be PENDING
        if (user.status != UserStatus.PENDING) {
            throw new IllegalArgumentException("User account is already onboarded or inactive.");
        }

        // 3. Verify OTP and update user details inside a transaction context
        transactionManager.execute(conn -> {
            boolean isValidOtp = tokenService.verifyOTP(conn, user.id, input.otp().trim(), "PATIENT_REGISTRATION");
            
            if (!isValidOtp) {
                throw new IllegalArgumentException("Invalid or expired OTP token.");
            }

            // OTP verified. Invalidate OTP token to prevent reuse.
            tokenService.invalidateOTP(conn, user.id, "PATIENT_REGISTRATION");

            // Update user credentials and status
            String passwordHash = cryptographicService.hashPassword(input.password());
            user.passwordHash = passwordHash;
            user.status = UserStatus.ACTIVE;

            // Save user modifications using transactional connection context
            userRepository.save(conn, user);
        });

        return Optional.of(new OnboardPatientResponseDTO(
            user.id,
            user.email,
            user.status.name()
        ));
    }
}
