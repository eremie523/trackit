package com.trackit.trackit.application.ports.services;

public interface ICryptographicService {
    String hashPassword(String password);
    boolean verifyPassword(String password, String hash);
}