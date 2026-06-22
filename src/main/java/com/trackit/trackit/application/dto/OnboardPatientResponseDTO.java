package com.trackit.trackit.application.dto;

public record OnboardPatientResponseDTO(
    String userId,
    String email,
    String status
) {}
