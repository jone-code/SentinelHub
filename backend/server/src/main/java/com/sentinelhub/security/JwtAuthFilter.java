package com.sentinelhub.security;

import com.sentinelhub.common.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtService.parse(header.substring(7));
                String userId = claims.getSubject();
                String tenantId = claims.get("tenant_id", String.class);
                @SuppressWarnings("unchecked")
                List<String> roles = claims.get("roles", List.class);
                var authorities = roles == null
                        ? List.<SimpleGrantedAuthority>of()
                        : roles.stream().map(r -> new SimpleGrantedAuthority("ROLE_" + r)).toList();
                var auth = new UsernamePasswordAuthenticationToken(userId, null, authorities);
                SecurityContextHolder.getContext().setAuthentication(auth);
                TenantContext.set(new TenantContext(tenantId, userId));
            } catch (Exception ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
