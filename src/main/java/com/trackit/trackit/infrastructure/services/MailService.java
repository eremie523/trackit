package com.trackit.trackit.infrastructure.services;

import com.trackit.trackit.application.ports.services.IMailService;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class MailService implements IMailService {

    @Override
    public void sendOnboardingEmail(String toEmail, String fullName, String otp) {
        System.out.println("==================================================");
        System.out.println("[MAIL SERVICE MOCK] Sending Email...");
        System.out.println("To: " + toEmail);
        System.out.println("Subject: Complete your TrackIt Account Registration");
        System.out.println("Body:");
        System.out.println("Hello " + fullName + ",");
        System.out.println("You have been registered as a patient on TrackIt.");
        System.out.println("Please complete your account setup by onboarding at the link below:");
        System.out.println("http://localhost:8080/trackit/onboard (or via API POST to /patients/onboard)");
        System.out.println("Use the following One-Time Password (OTP) to complete registration:");
        System.out.println("OTP: " + otp);
        System.out.println("Note: This OTP is valid for 3 days.");
        System.out.println("==================================================");
    }
}
