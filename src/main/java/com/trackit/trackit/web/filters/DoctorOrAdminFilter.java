package com.trackit.trackit.web.filters;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.Optional;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.trackit.trackit.application.ports.services.IJwtService;
import com.trackit.trackit.application.ports.repositories.IUserRepository;
import com.trackit.trackit.application.dto.DecodedToken;
import com.trackit.trackit.core.domains.entities.user.User;
import com.trackit.trackit.core.domains.entities.user.valueObjects.UserRole;

@WebFilter(filterName = "DoctorOrAdminFilter", urlPatterns = { "/patients/register", "/patients" })
public class DoctorOrAdminFilter implements Filter {

    @Inject
    private IJwtService jwtService;

    @Inject
    private IUserRepository userRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        if (this.jwtService == null) {
            this.jwtService = new com.trackit.trackit.infrastructure.services.JwtService();
        }
        if (this.userRepository == null) {
            this.userRepository = new com.trackit.trackit.infrastructure.persistence.mysql.UserRepository();
        }
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if ("OPTIONS".equalsIgnoreCase(httpRequest.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = httpRequest.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "Authorization header is missing or does not contain a Bearer token.");
            return;
        }

        String token = authHeader.substring(7).trim();
        Optional<DecodedToken> decodedOpt = jwtService.decodeToken(token);
        if (decodedOpt.isEmpty()) {
            sendError(httpResponse, HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired access token.");
            return;
        }

        DecodedToken decoded = decodedOpt.get();
        String roleStr = decoded.role();
        String userId = decoded.userId();

        boolean isAuthorized = false;

        if (roleStr != null && !roleStr.isEmpty()) {
            try {
                UserRole role = UserRole.valueOf(roleStr.toUpperCase());
                if (role == UserRole.DOCTOR || role == UserRole.SUPER_ADMIN) {
                    isAuthorized = true;
                }
            } catch (IllegalArgumentException e) {
                // Ignore and fallback to DB check
            }
        }

        if (!isAuthorized) {
            Optional<User> userOpt = userRepository.findById(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                if (user.role == UserRole.DOCTOR || user.role == UserRole.SUPER_ADMIN) {
                    isAuthorized = true;
                }
            }
        }

        if (isAuthorized) {
            httpRequest.setAttribute("currentUserId", userId);
            httpRequest.setAttribute("currentUserRole", roleStr);
            chain.doFilter(request, response);
        } else {
            sendError(httpResponse, HttpServletResponse.SC_FORBIDDEN, "Forbidden: Only users with DOCTOR or SUPER_ADMIN role can perform this operation.");
        }
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), Map.of("error", message));
    }

    @Override
    public void destroy() {
    }
}
