
package com.trackit.trackit.core.domains.entities.user.dto;

import com.trackit.trackit.core.domains.entities.user.valueObjects.Gender;
import com.trackit.trackit.core.domains.entities.user.valueObjects.UserRole;
import com.trackit.trackit.core.domains.entities.user.valueObjects.UserStatus;
import java.time.Instant;

public record ReconstructUserDTO(
    String id, 
    String email, 
    String passwordHash, 
    String fullName, 
    UserRole role, 
    UserStatus status, 
    String nationalID,
    Gender gender,
    String phoneNumber,
    Instant createdAt,
    Instant updatedAt
) {}
