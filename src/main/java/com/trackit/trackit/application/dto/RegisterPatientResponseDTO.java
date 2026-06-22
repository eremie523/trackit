package com.trackit.trackit.application.dto;

public record RegisterPatientResponseDTO(
    String userId,
    String patientId,
    String email,
    String fullName
) {}
