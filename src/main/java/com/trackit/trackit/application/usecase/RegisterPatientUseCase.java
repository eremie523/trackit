package com.trackit.trackit.application.usecase;

import java.util.Optional;
import java.util.UUID;

import com.trackit.trackit.application.dto.RegisterPatientInputDTO;
import com.trackit.trackit.application.dto.RegisterPatientResponseDTO;
import com.trackit.trackit.application.ports.repositories.IPatientRepository;
import com.trackit.trackit.application.ports.repositories.IUserRepository;
import com.trackit.trackit.application.ports.services.ICryptographicService;
import com.trackit.trackit.application.ports.services.IMailService;
import com.trackit.trackit.application.ports.services.ITokenService;
import com.trackit.trackit.application.ports.services.ITransactionManager;
import com.trackit.trackit.core.domains.entities.user.Patient;
import com.trackit.trackit.core.domains.entities.user.User;
import com.trackit.trackit.core.domains.entities.user.dto.CreatePatientDTO;
import com.trackit.trackit.core.domains.entities.user.dto.CreateUserDTO;
import com.trackit.trackit.core.domains.entities.user.valueObjects.UserRole;
import com.trackit.trackit.core.domains.entities.user.valueObjects.UserStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class RegisterPatientUseCase implements IUseCase<RegisterPatientInputDTO, RegisterPatientResponseDTO> {

    private final IUserRepository userRepository;
    private final IPatientRepository patientRepository;
    private final ICryptographicService cryptographicService;
    private final ITokenService tokenService;
    private final IMailService mailService;
    private final ITransactionManager transactionManager;

    @Inject
    public RegisterPatientUseCase(IUserRepository userRepository,
            IPatientRepository patientRepository,
            ICryptographicService cryptographicService,
            ITokenService tokenService,
            IMailService mailService,
            ITransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.patientRepository = patientRepository;
        this.cryptographicService = cryptographicService;
        this.tokenService = tokenService;
        this.mailService = mailService;
        this.transactionManager = transactionManager;
    }

    protected RegisterPatientUseCase() {
        this.userRepository = null;
        this.patientRepository = null;
        this.cryptographicService = null;
        this.tokenService = null;
        this.mailService = null;
        this.transactionManager = null;
    }

    @Override
    public Optional<RegisterPatientResponseDTO> execute(RegisterPatientInputDTO input) {
        if (input == null || input.email() == null) {
            return Optional.empty();
        }

        // Validate that user email is unique
        Optional<User> existingUser = userRepository.findByEmail(input.email().trim());
        if (existingUser.isPresent()) {
            throw new IllegalArgumentException("User with this email already exists.");
        }

        // Generate temporary random password hash (since user is PENDING and must onboard)
        String temporaryPassword = "TEMP_PASS_" + UUID.randomUUID().toString();
        String passwordHash = cryptographicService.hashPassword(temporaryPassword);

        // Create User entity
        CreateUserDTO userDTO = new CreateUserDTO(
            input.email().trim(),
            passwordHash,
            input.fullName(),
            UserRole.PATIENT,
            input.nationalID(),
            input.gender(),
            input.phoneNumber()
        );
        User user = User.create(userDTO);
        user.status = UserStatus.PENDING; // Must complete onboarding before logging in

        final String[] patientIdHolder = new String[1];
        final String[] plainOtpHolder = new String[1];
        
        // Execute inserts and OTP generation inside a single database transaction context
        transactionManager.execute(conn -> {
            // Save user using transaction connection context
            User savedUser = userRepository.save(conn, user);

            // Create Patient entity using the created user's ID
            CreatePatientDTO patientDTO = new CreatePatientDTO(
                savedUser.id,
                input.dob(),
                input.bloodType(),
                input.genotype(),
                input.isMarried(),
                input.address()
            );
            Patient patient = Patient.create(patientDTO);

            // Save patient using transaction connection context
            Patient savedPatient = patientRepository.save(conn, patient);
            patientIdHolder[0] = savedPatient.id;

            // Generate and save token using transaction connection context
            plainOtpHolder[0] = tokenService.generateAndSaveOTP(conn, savedUser.id, "PATIENT_REGISTRATION");
        });

        // Trigger onboarding notification (via mock email)
        mailService.sendOnboardingEmail(user.email, user.fullName, plainOtpHolder[0]);

        return Optional.of(new RegisterPatientResponseDTO(
            user.id,
            patientIdHolder[0],
            user.email,
            user.fullName
        ));
    }
}
