package com.sentinelhub.api.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UpdateNacRadiusRequest(
        Boolean enabled,
        String serverHost,
        @Min(1) @Max(65535) Integer authPort,
        @Min(1) @Max(65535) Integer acctPort,
        String secret,
        String nasIdentifier,
        String vlanAllowed,
        String vlanRestricted,
        String vlanDenied
) {
    public boolean resolvedEnabled() {
        return enabled != null && enabled;
    }

    public int resolvedAuthPort() {
        return authPort != null ? authPort : 1812;
    }

    public int resolvedAcctPort() {
        return acctPort != null ? acctPort : 1813;
    }
}
