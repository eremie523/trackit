package com.trackit.trackit.application.dto;

import java.time.Instant;

public record PatientSearchItemDTO(
    String patientId,
    String userId,
    String fullName,
    String nationalID,
    Instant dob,
    String gender,
    String phoneNumber
) {}
