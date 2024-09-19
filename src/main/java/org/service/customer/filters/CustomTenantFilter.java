package org.service.customer.filters;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.service.customer.service.CustomUserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class CustomTenantFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String tenantId = request.getHeader("X-Tenant-ID"); // Assume tenantId comes from a custom header

        if (tenantId != null) {
            CustomUserDetailsService.setTenantContext(tenantId);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            CustomUserDetailsService.clearTenantContext();
        }
    }
}
