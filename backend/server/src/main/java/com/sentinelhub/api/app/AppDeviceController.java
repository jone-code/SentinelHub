package com.sentinelhub.api.app;

import com.sentinelhub.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Mobile device view APIs.
 */
@RestController
@RequestMapping("/api/app/v1/devices")
public class AppDeviceController {

    @GetMapping
    public ApiResponse<List<?>> myDevices() {
        return ApiResponse.ok(Collections.emptyList());
    }

    @GetMapping("/summary")
    public ApiResponse<Map<String, Object>> summary() {
        return ApiResponse.ok(Map.of(
                "total", 0,
                "online", 0,
                "compliance_rate", 0
        ));
    }
}
