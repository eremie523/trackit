package com.trackit.trackit.application.ports.services;

public interface IMailService {
    void sendOnboardingEmail(String toEmail, String fullName, String otp);
}
