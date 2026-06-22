package com.trackit.trackit.application.dto;

import java.time.Instant;

import com.trackit.trackit.core.domains.entities.user.valueObjects.Address;
import com.trackit.trackit.core.domains.entities.user.valueObjects.BloodType;
import com.trackit.trackit.core.domains.entities.user.valueObjects.Gender;
import com.trackit.trackit.core.domains.entities.user.valueObjects.Genotype;

public record RegisterPatientInputDTO(
                String email,
                String fullName,
                String nationalID,
                Gender gender,
                String phoneNumber,
                Instant dob,
                BloodType bloodType,
                Genotype genotype,
                boolean isMarried,
                Address address) {

}
