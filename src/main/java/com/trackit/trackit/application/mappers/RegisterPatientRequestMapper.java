package com.trackit.trackit.application.mappers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.trackit.trackit.application.dto.RegisterPatientInputDTO;
import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;

public final class RegisterPatientRequestMapper {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private RegisterPatientRequestMapper() {}

    public static RegisterPatientInputDTO toInputDTO(HttpServletRequest request) throws IOException {
        if (request == null) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        String line;
        try (BufferedReader reader = request.getReader()) {
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }

        String json = sb.toString();
        if (json.trim().isEmpty()) {
            throw new IllegalArgumentException("Request body cannot be empty");
        }

        return objectMapper.readValue(json, RegisterPatientInputDTO.class);
    }
}
