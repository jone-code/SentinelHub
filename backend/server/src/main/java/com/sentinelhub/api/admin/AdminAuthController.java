package com.sentinelhub.api.admin;

import com.sentinelhub.api.admin.dto.LoginRequest;
import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.module.identity.IdentityService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/v1/auth")
public class AdminAuthController {

    private final IdentityService identityService;

    public AdminAuthController(IdentityService identityService) {
        this.identityService = identityService;
    }

    @PostMapping("/login")
    public ApiResponse<Map<String, Object>> login(@Valid @RequestBody LoginRequest request,
                                                   HttpServletRequest httpRequest) {
        String ip = httpRequest.getRemoteAddr();
        Map<String, Object> result = identityService.login(
                request.email(), request.password(), request.resolvedTenantSlug(), ip);
        return ApiResponse.ok(result);
    }
}
