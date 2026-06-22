package com.trackit.trackit.web.servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.BufferedReader;
import java.util.Optional;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.trackit.trackit.application.usecase.OnboardPatientUseCase;
import com.trackit.trackit.application.dto.OnboardPatientInputDTO;
import com.trackit.trackit.application.dto.OnboardPatientResponseDTO;

@WebServlet(name = "OnboardPatientServlet", urlPatterns = { "/patients/onboard" })
public class OnboardPatientServlet extends HttpServlet {

    @Inject
    private OnboardPatientUseCase onboardPatientUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public void init() throws ServletException {
        if (this.onboardPatientUseCase == null) {
            System.out.println("[OnboardPatientServlet] OnboardPatientUseCase was null. Instantiating manually...");
            com.trackit.trackit.application.ports.repositories.IUserRepository userRepo = new com.trackit.trackit.infrastructure.persistence.mysql.UserRepository();
            com.trackit.trackit.application.ports.repositories.ITokenRepository tokenRepo = new com.trackit.trackit.infrastructure.persistence.mysql.TokenRepository();
            com.trackit.trackit.application.ports.services.ICryptographicService crypto = new com.trackit.trackit.infrastructure.services.CryptographicService();
            com.trackit.trackit.application.ports.services.ITokenService tokenService = new com.trackit.trackit.infrastructure.services.TokenService(tokenRepo, crypto);
            com.trackit.trackit.application.ports.services.ITransactionManager txManager = new com.trackit.trackit.infrastructure.services.TransactionManager();
            this.onboardPatientUseCase = new com.trackit.trackit.application.usecase.OnboardPatientUseCase(userRepo, tokenService, crypto, txManager);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            // Read JSON body
            StringBuilder sb = new StringBuilder();
            String line;
            try (BufferedReader reader = request.getReader()) {
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
            }

            String json = sb.toString();
            if (json.trim().isEmpty()) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                objectMapper.writeValue(response.getWriter(), Map.of("error", "Request body cannot be empty."));
                return;
            }

            OnboardPatientInputDTO inputDTO = objectMapper.readValue(json, OnboardPatientInputDTO.class);

            if (inputDTO.email() == null || inputDTO.email().trim().isEmpty() ||
                inputDTO.password() == null || inputDTO.password().trim().isEmpty() ||
                inputDTO.otp() == null || inputDTO.otp().trim().isEmpty()) {

                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                objectMapper.writeValue(response.getWriter(), Map.of("error", "Email, password, and otp are required."));
                return;
            }

            Optional<OnboardPatientResponseDTO> responseOpt = onboardPatientUseCase.execute(inputDTO);

            if (responseOpt.isPresent()) {
                response.setStatus(HttpServletResponse.SC_OK);
                objectMapper.writeValue(response.getWriter(), responseOpt.get());
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                objectMapper.writeValue(response.getWriter(), Map.of("error", "Could not complete onboarding."));
            }
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(response.getWriter(), Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.err.println("[OnboardPatientServlet] Unexpected error: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }
}
