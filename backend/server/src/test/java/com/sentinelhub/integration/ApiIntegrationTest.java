package com.sentinelhub.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void health_returnsHealthy() {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/health",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(0, response.getBody().get("code"));
        @SuppressWarnings("unchecked")
        Map<String, String> data = (Map<String, String>) response.getBody().get("data");
        assertEquals("healthy", data.get("status"));
    }

    @Test
    void adminLogin_andRtcConfig() {
        Map<String, String> loginBody = Map.of(
                "email", "admin@test.local",
                "password", "testpass123",
                "tenant_slug", "test"
        );
        ResponseEntity<Map<String, Object>> loginResponse = restTemplate.exchange(
                "/api/admin/v1/auth/login",
                HttpMethod.POST,
                jsonEntity(loginBody),
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, loginResponse.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> loginData = (Map<String, Object>) loginResponse.getBody().get("data");
        String token = (String) loginData.get("access_token");
        assertNotNull(token);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<Map<String, Object>> rtcResponse = restTemplate.exchange(
                "/api/admin/v1/remote/rtc-config",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, rtcResponse.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> rtcData = (Map<String, Object>) rtcResponse.getBody().get("data");
        assertEquals(true, rtcData.get("turn_enabled"));
        assertEquals(true, rtcData.get("turn_ephemeral"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> iceServers = (List<Map<String, Object>>) rtcData.get("ice_servers");
        assertTrue(iceServers.size() >= 2);
    }

    @Test
    void clientRtcConfig_isPublic() {
        ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                "/api/client/v1/service/remote/rtc-config",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {}
        );

        assertEquals(HttpStatus.OK, response.getStatusCode());
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        assertNotNull(data.get("ice_servers"));
        assertEquals(true, data.get("turn_enabled"));
    }

    private static HttpEntity<Map<String, String>> jsonEntity(Map<String, String> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
