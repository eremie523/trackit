package com.trackit.trackit.web.servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.trackit.trackit.application.usecase.RegisterPatientUseCase;
import com.trackit.trackit.application.dto.RegisterPatientInputDTO;
import com.trackit.trackit.application.dto.RegisterPatientResponseDTO;
import com.trackit.trackit.application.mappers.RegisterPatientRequestMapper;

@WebServlet(name = "PatientServlet", urlPatterns = { "/patients/register", "/patients" })
public class PatientServlet extends HttpServlet {

    @Inject
    private RegisterPatientUseCase registerPatientUseCase;

    @Inject
    private com.trackit.trackit.application.usecase.SearchPatientsUseCase searchPatientsUseCase;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
    public void init() throws ServletException {
        if (this.registerPatientUseCase == null) {
            System.out.println("[PatientServlet] RegisterPatientUseCase was null. Instantiating manually...");
            com.trackit.trackit.application.ports.repositories.IUserRepository userRepo = new com.trackit.trackit.infrastructure.persistence.mysql.UserRepository();
            com.trackit.trackit.application.ports.repositories.IPatientRepository patientRepo = new com.trackit.trackit.infrastructure.persistence.mysql.PatientRepository();
            com.trackit.trackit.application.ports.services.ICryptographicService crypto = new com.trackit.trackit.infrastructure.services.CryptographicService();
            com.trackit.trackit.application.ports.repositories.ITokenRepository tokenRepo = new com.trackit.trackit.infrastructure.persistence.mysql.TokenRepository();
            com.trackit.trackit.application.ports.services.ITokenService tokenService = new com.trackit.trackit.infrastructure.services.TokenService(tokenRepo, crypto);
            com.trackit.trackit.application.ports.services.IMailService mailService = new com.trackit.trackit.infrastructure.services.MailService();
            com.trackit.trackit.application.ports.services.ITransactionManager txManager = new com.trackit.trackit.infrastructure.services.TransactionManager();
            this.registerPatientUseCase = new com.trackit.trackit.application.usecase.RegisterPatientUseCase(userRepo, patientRepo, crypto, tokenService, mailService, txManager);
        }
        if (this.searchPatientsUseCase == null) {
            System.out.println("[PatientServlet] SearchPatientsUseCase was null. Instantiating manually...");
            com.trackit.trackit.application.ports.repositories.IPatientRepository patientRepo = new com.trackit.trackit.infrastructure.persistence.mysql.PatientRepository();
            this.searchPatientsUseCase = new com.trackit.trackit.application.usecase.SearchPatientsUseCase(patientRepo);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String path = request.getServletPath();
        if (!"/patients".equals(path)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            objectMapper.writeValue(response.getWriter(), Map.of("error", "Not found"));
            return;
        }

        try {
            String firstName = request.getParameter("firstName");
            String lastName = request.getParameter("lastName");
            String dob = request.getParameter("dob");
            String nationalID = request.getParameter("nationalID");
            String phoneNumber = request.getParameter("phoneNumber");
            String gender = request.getParameter("gender");

            com.trackit.trackit.application.dto.SearchPatientFilterDTO filter =
                    new com.trackit.trackit.application.dto.SearchPatientFilterDTO(
                            firstName, lastName, dob, nationalID, phoneNumber, gender
                    );

            Optional<java.util.List<com.trackit.trackit.application.dto.PatientSearchItemDTO>> resultsOpt =
                    searchPatientsUseCase.execute(filter);

            if (resultsOpt.isPresent()) {
                response.setStatus(HttpServletResponse.SC_OK);
                objectMapper.writeValue(response.getWriter(), resultsOpt.get());
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                objectMapper.writeValue(response.getWriter(), Map.of("error", "Failed to retrieve patients"));
            }
        } catch (Exception e) {
            System.err.println("[PatientServlet] GET error: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String path = request.getServletPath();
        if (!"/patients/register".equals(path)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            objectMapper.writeValue(response.getWriter(), Map.of("error", "Not found"));
            return;
        }

        try {
            RegisterPatientInputDTO inputDTO = RegisterPatientRequestMapper.toInputDTO(request);
            
            if (inputDTO.email() == null || inputDTO.email().trim().isEmpty() ||
                inputDTO.fullName() == null || inputDTO.fullName().trim().isEmpty()) {
                
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                objectMapper.writeValue(response.getWriter(), Map.of("error", "Email and fullName are required."));
                return;
            }

            Optional<RegisterPatientResponseDTO> responseOpt = registerPatientUseCase.execute(inputDTO);

            if (responseOpt.isPresent()) {
                response.setStatus(HttpServletResponse.SC_CREATED);
                objectMapper.writeValue(response.getWriter(), responseOpt.get());
            } else {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                objectMapper.writeValue(response.getWriter(), Map.of("error", "Could not register patient."));
            }
        } catch (IllegalArgumentException e) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            objectMapper.writeValue(response.getWriter(), Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.err.println("[PatientServlet] Unexpected error: " + e.getMessage());
            e.printStackTrace();
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            objectMapper.writeValue(response.getWriter(), Map.of("error", "Internal server error: " + e.getMessage()));
        }
    }
}
