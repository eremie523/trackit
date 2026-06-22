package com.trackit.trackit.web.servlets;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.Optional;

import com.trackit.trackit.application.usecase.LoginUseCase;
import com.trackit.trackit.application.dto.LoginInputDTO;
import com.trackit.trackit.application.dto.LoginResponseDTO;
import com.trackit.trackit.application.mappers.LoginRequestMapper;

@WebServlet(name = "AuthServlet", urlPatterns = { "/login", "/logout" })
public class AuthServlet extends HttpServlet {

    @Inject
    private LoginUseCase loginUseCase;

    @Override
    public void init() throws ServletException {
        // Fallback manual wiring if CDI Injection is null due to server bootstrap
        // timings
        if (this.loginUseCase == null) {
            System.out.println("[AuthServlet] ILoginUseCase was null. Instantiating components manually...");
            com.trackit.trackit.application.ports.repositories.IUserRepository repo = new com.trackit.trackit.infrastructure.persistence.mysql.UserRepository();
            com.trackit.trackit.application.ports.services.ICryptographicService crypto = new com.trackit.trackit.infrastructure.services.CryptographicService();
            com.trackit.trackit.application.ports.services.IJwtService jwt = new com.trackit.trackit.infrastructure.services.JwtService();
            this.loginUseCase = new com.trackit.trackit.application.usecase.LoginUseCase(repo, crypto, jwt);
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String path = request.getServletPath();
        if ("/logout".equals(path)) {
            handleLogout(request, response);
            return;
        }

        // GET /login is not allowed for an API
        response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        response.getWriter().write("{\"error\": \"Method not allowed. Use POST to /login for authentication.\"}");
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String path = request.getServletPath();
        if ("/logout".equals(path)) {
            handleLogout(request, response);
            return;
        }

        // Otherwise handle POST /login
        LoginInputDTO inputDTO = LoginRequestMapper.toInputDTO(request);

        if (inputDTO.email() == null || inputDTO.password() == null
                || inputDTO.email().isEmpty() || inputDTO.password().isEmpty()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.getWriter().write("{\"error\": \"Email and password are required.\"}");
            return;
        }

        Optional<LoginResponseDTO> loginResponseOpt = loginUseCase.execute(inputDTO);

        if (loginResponseOpt.isPresent()) {
            LoginResponseDTO res = loginResponseOpt.get();
            response.setStatus(HttpServletResponse.SC_OK);

            // Return access token and user type
            String json = String.format(
                    "{\"accessToken\": \"%s\", \"userType\": \"%s\", \"email\": \"%s\", \"fullName\": \"%s\"}",
                    res.accessToken(), res.userType(), res.email(), res.fullName());
            response.getWriter().write(json);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\": \"Invalid email or password.\"}");
        }
    }

    private void handleLogout(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().write("{\"message\": \"Logged out successfully.\"}");
    }
}
