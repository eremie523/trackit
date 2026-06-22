package com.trackit.trackit.core.domains.entities.user.dto;

import java.time.Instant;

import com.trackit.trackit.core.domains.entities.user.valueObjects.Address;
import com.trackit.trackit.core.domains.entities.user.valueObjects.BloodType;
import com.trackit.trackit.core.domains.entities.user.valueObjects.Genotype;

public record CreatePatientDTO(
                String userId,
                Instant dob,
                BloodType bloodType,
                Genotype genotype,
                boolean isMarried,
                Address address) {

}
