package com.trackit.trackit.core.domains.entities.user.dto;

import java.time.Instant;

import com.trackit.trackit.core.domains.entities.user.valueObjects.Address;
import com.trackit.trackit.core.domains.entities.user.valueObjects.BloodType;
import com.trackit.trackit.core.domains.entities.user.valueObjects.Genotype;

public record ReconstructPatientDTO(
                String id,
                String userId,
                boolean isMarried,
                BloodType bloodType,
                Genotype genotype,
                Instant dob,
                int age,
                Address address) {

}
