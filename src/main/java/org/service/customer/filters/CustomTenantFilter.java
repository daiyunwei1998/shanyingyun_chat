package org.service.customer.filters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.service.customer.service.CustomUserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
public class CustomTenantFilter extends OncePerRequestFilter {
    private final ObjectMapper objectMapper = new ObjectMapper();


    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String tenantId = request.getHeader("X-Tenant-ID"); // Assume tenantId comes from a custom header
        log.error(tenantId);
        if (tenantId != null) {
            CustomUserDetailsService.setTenantContext(tenantId);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            CustomUserDetailsService.clearTenantContext();
        }
    }
    private JsonNode parseRequestBody(HttpServletRequest request) {
        try {
            return objectMapper.readTree(request.getInputStream());
        } catch (IOException e) {
            return null;
        }
    }
}
