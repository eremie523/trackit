package com.trackit.trackit.application.dto;

public record SearchPatientFilterDTO(
    String firstName,
    String lastName,
    String dob,
    String nationalID,
    String phoneNumber,
    String gender
) {}
