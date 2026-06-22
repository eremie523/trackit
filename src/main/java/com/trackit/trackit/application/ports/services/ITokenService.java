package com.trackit.trackit.application.ports.services;

import java.sql.Connection;
import java.util.Optional;

public interface ITokenService {
    String generateAndSaveOTP(String targetId, String purpose);
    String generateAndSaveOTP(Connection conn, String targetId, String purpose);
    boolean verifyOTP(String targetId, String plainOtp, String purpose);
    boolean verifyOTP(Connection conn, String targetId, String plainOtp, String purpose);
    void invalidateOTP(String targetId, String purpose);
    void invalidateOTP(Connection conn, String targetId, String purpose);
}
