package com.sentinelhub.module.identity;

import com.sentinelhub.module.audit.AuditService;
import com.sentinelhub.module.identity.domain.User;
import com.sentinelhub.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class IdentityService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuditService auditService;

    public IdentityService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                           JwtService jwtService, AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.auditService = auditService;
    }

    public Map<String, Object> login(String email, String password, String tenantSlug, String ip) {
        String tenantId = userRepository.findTenantIdBySlug(tenantSlug)
                .orElseThrow(() -> new IllegalArgumentException("tenant not found"));
        User user = userRepository.findByEmail(tenantId, email)
                .orElseThrow(() -> new IllegalArgumentException("invalid credentials"));
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new IllegalArgumentException("invalid credentials");
        }
        List<String> roles = userRepository.findRoleCodes(user.id());
        String token = jwtService.createToken(user.id(), tenantId, user.email(), roles);
        auditService.log(tenantId, "user", user.id(), "identity.login", "user", user.id(),
                Map.of("email", email), ip);
        return Map.of(
                "access_token", token,
                "token_type", "Bearer",
                "expires_in", 24 * 3600,
                "user", Map.of(
                        "id", user.id(),
                        "email", user.email(),
                        "name", user.name(),
                        "roles", roles
                )
        );
    }

    public String resolveTenantByRegistrationToken(String token) {
        var ids = userRepository.findTenantIdByRegistrationToken(token);
        return ids.orElseThrow(() -> new IllegalArgumentException("invalid tenant token"));
    }
}
