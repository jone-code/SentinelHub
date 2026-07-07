package com.sentinelhub.api.admin;

import com.sentinelhub.common.dto.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;

/**
 * Device management APIs for admin console.
 */
@RestController
@RequestMapping("/api/admin/v1/devices")
public class AdminDeviceController {

    @GetMapping
    public ApiResponse<List<?>> listDevices() {
        return ApiResponse.ok(Collections.emptyList());
    }
}
