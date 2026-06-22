package com.trackit.trackit.infrastructure.bootstrap;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import jakarta.inject.Inject;
import com.trackit.trackit.application.ports.repositories.IUserRepository;
import com.trackit.trackit.application.ports.services.ICryptographicService;
import com.trackit.trackit.core.domains.entities.user.User;
import com.trackit.trackit.core.domains.entities.user.dto.CreateUserDTO;
import com.trackit.trackit.core.domains.entities.user.valueObjects.Gender;
import com.trackit.trackit.core.domains.entities.user.valueObjects.UserRole;
import com.trackit.trackit.core.domains.entities.user.valueObjects.UserStatus;
import com.trackit.trackit.infrastructure.config.Env;
import com.trackit.trackit.infrastructure.persistence.mysql.UserRepository;
import com.trackit.trackit.infrastructure.services.CryptographicService;
import java.util.Optional;

@WebListener
public class DatabaseSeeder implements ServletContextListener {

    @Inject
    private IUserRepository userRepository;

    @Inject
    private ICryptographicService cryptographicService;

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("[Bootstrap] Application starting. Initializing database seeder...");

        // Fallback to manual instantiation if CDI context is not fully ready in the
        // Servlet Listener
        IUserRepository repo = this.userRepository;
        if (repo == null) {
            System.out.println("[Bootstrap] CDI Injection was null for IUserRepository. Instantiating manually...");
            repo = new UserRepository();
        }

        ICryptographicService crypto = this.cryptographicService;
        if (crypto == null) {
            System.out
                    .println("[Bootstrap] CDI Injection was null for ICryptographicService. Instantiating manually...");
            crypto = new CryptographicService();
        }

        try {
            seedSuperAdmin(repo, crypto);
        } catch (Exception e) {
            System.err.println("[Bootstrap] CRITICAL: Failed to seed database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
        // Shutdown MySQL cleanup thread cleanly
        try {
            com.mysql.cj.jdbc.AbandonedConnectionCleanupThread.checkedShutdown();
        } catch (Exception e) {
            // ignore
        }
    }

    private void seedSuperAdmin(IUserRepository repo, ICryptographicService crypto) {
        String email = Env.get("SUPER_ADMIN_EMAIL", "admin@trackit.com").trim();
        String password = Env.get("SUPER_ADMIN_PASSWORD", "AdminSecurePassword123!");
        String fullName = Env.get("SUPER_ADMIN_NAME", "Super Admin");
        String nationalID = Env.get("SUPER_ADMIN_NATIONAL_ID", "1234567890");
        String genderStr = Env.get("SUPER_ADMIN_GENDER", "MALE");
        String phoneNumber = Env.get("SUPER_ADMIN_PHONE", "1234567890");

        Optional<User> existingUser = repo.findByEmail(email);
        if (existingUser.isPresent()) {
            System.out.println("[Bootstrap] Super admin already exists with email: " + email + ". Skipping seeder.");
            return;
        }

        System.out.println("[Bootstrap] Super admin not found. Seeding user: " + email);

        String passwordHash = crypto.hashPassword(password);
        Gender gender;
        try {
            gender = Gender.valueOf(genderStr.toUpperCase());
        } catch (Exception e) {
            gender = Gender.MALE;
        }

        CreateUserDTO dto = new CreateUserDTO(
                email,
                passwordHash,
                fullName,
                UserRole.SUPER_ADMIN,
                nationalID,
                gender,
                phoneNumber);

        User superAdmin = User.create(dto);
        // Ensure status is ACTIVE so they can log in
        superAdmin.status = UserStatus.ACTIVE;

        repo.save(superAdmin);
        System.out.println("[Bootstrap] Super admin seeded successfully.");
    }
}
