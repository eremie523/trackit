package com.trackit.trackit.application.mappers;

import com.trackit.trackit.application.dto.LoginInputDTO;
import jakarta.servlet.http.HttpServletRequest;

public final class LoginRequestMapper {

    private LoginRequestMapper() {}

    public static LoginInputDTO toInputDTO(HttpServletRequest request) {
        if (request == null) {
            return new LoginInputDTO(null, null);
        }

        String email = request.getParameter("email");
        String password = request.getParameter("password");

        // If fields are null, inspect if the request has JSON payload
        if ((email == null || password == null) && request.getContentType() != null 
                && request.getContentType().contains("application/json")) {
            try {
                StringBuilder sb = new StringBuilder();
                String line;
                try (java.io.BufferedReader reader = request.getReader()) {
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                }
                String body = sb.toString();
                String parsedEmail = extractJsonValue(body, "email");
                String parsedPassword = extractJsonValue(body, "password");
                
                if (parsedEmail != null) email = parsedEmail;
                if (parsedPassword != null) password = parsedPassword;
            } catch (Exception e) {
                // Fail silently and fallback to standard parameters
            }
        }

        return new LoginInputDTO(email, password);
    }

    private static String extractJsonValue(String json, String key) {
        int keyIndex = json.indexOf("\"" + key + "\"");
        if (keyIndex == -1) return null;
        int colonIndex = json.indexOf(":", keyIndex);
        if (colonIndex == -1) return null;
        int startQuote = json.indexOf("\"", colonIndex);
        if (startQuote == -1) return null;
        int endQuote = json.indexOf("\"", startQuote + 1);
        if (endQuote == -1) return null;
        return json.substring(startQuote + 1, endQuote);
    }
}
