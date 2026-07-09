package com.sentinelhub.api.app;

import com.sentinelhub.common.dto.ApiResponse;
import com.sentinelhub.module.mdm.MdmService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/app/v1/mdm")
public class AppMdmController {

    private final MdmService mdmService;

    public AppMdmController(MdmService mdmService) {
        this.mdmService = mdmService;
    }

    @GetMapping("/profiles")
    public ApiResponse<List<Map<String, Object>>> profiles(
            @RequestHeader(value = "X-Client-Id", required = false) String clientId) {
        if (clientId == null || clientId.isBlank()) {
            return ApiResponse.ok(List.of());
        }
        return ApiResponse.ok(mdmService.getProfilesForDevice(clientId));
    }

    @PostMapping("/profiles/applied")
    public ApiResponse<Map<String, Object>> markApplied(@RequestBody Map<String, Object> body) {
        String clientId = stringVal(body.get("client_id"));
        String profileId = stringVal(body.get("profile_id"));
        if (clientId == null || profileId == null) {
            throw new IllegalArgumentException("client_id and profile_id required");
        }
        return ApiResponse.ok(mdmService.reportAppliedForClient(clientId, profileId));
    }

    private static String stringVal(Object o) {
        return o != null ? o.toString() : null;
    }
}
