package com.trackit.trackit.application.dto;

public record LoginResponseDTO(
    String accessToken,
    String userType,
    String email,
    String fullName
) {}
