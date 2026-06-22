package com.trackit.trackit.core.domains.entities.user.dto;

import com.trackit.trackit.core.domains.entities.user.valueObjects.Gender;
import com.trackit.trackit.core.domains.entities.user.valueObjects.UserRole;

public record CreateUserDTO(
    String email, 
    String passwordHash, 
    String fullName,
    UserRole role,
    String nationalID,
    Gender gender,
    String phoneNumber
) {}