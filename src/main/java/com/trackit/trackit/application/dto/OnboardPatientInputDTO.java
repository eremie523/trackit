package com.trackit.trackit.application.dto;

public record OnboardPatientInputDTO(
    String email,
    String password,
    String otp
) {}
