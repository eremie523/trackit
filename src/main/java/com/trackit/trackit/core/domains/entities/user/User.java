package com.trackit.trackit.core.domains.entities.user;

import com.trackit.trackit.core.domains.entities.user.valueObjects.Gender;
import com.trackit.trackit.core.domains.entities.user.valueObjects.UserRole;
import com.trackit.trackit.core.domains.entities.user.valueObjects.UserStatus;
import com.trackit.trackit.core.domains.entities.user.dto.CreateUserDTO;
import com.trackit.trackit.core.domains.entities.user.dto.ReconstructUserDTO;

import java.time.Instant;
import java.util.UUID;

public class User {
    public final String id;
    public final String email;
    public String passwordHash;
    public String fullName;
    public UserRole role;
    public UserStatus status;
    public final String nationalID;
    public final Gender gender;
    public String phoneNumber;
    public final Instant createdAt;
    public final Instant updatedAt;
    public final boolean isNew;
    
    private User(CreateUserDTO input) {
        this.id = UUID.randomUUID().toString();
        this.email = input.email();
        this.passwordHash = input.passwordHash();
        this.fullName = input.fullName();
        this.role = input.role();
        this.status = UserStatus.PENDING;
        this.nationalID = input.nationalID();
        this.gender = input.gender();
        this.phoneNumber = input.phoneNumber();
        this.createdAt = null;
        this.updatedAt = null;
        this.isNew = true;
    }
    
    private User(ReconstructUserDTO input) {
        this.id = input.id();
        this.email = input.email();
        this.passwordHash = input.passwordHash();
        this.fullName = input.fullName();
        this.role = input.role();
        this.status = input.status();
        this.nationalID = input.nationalID();
        this.gender = input.gender();
        this.phoneNumber = input.phoneNumber();
        this.createdAt = input.createdAt();
        this.updatedAt = input.updatedAt();
        this.isNew = false;
    }
    
    public static User create(CreateUserDTO input) {
        // Validation at this layer
        return new User(input);
    }
    
    public static User reconstruct(ReconstructUserDTO input) {
        // From the DB Mostly
        return new User(input);
    }
}