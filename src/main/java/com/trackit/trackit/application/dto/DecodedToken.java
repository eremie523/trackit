package com.trackit.trackit.application.dto;

public record DecodedToken(
    String userId,
    String email,
    String role
) {}
