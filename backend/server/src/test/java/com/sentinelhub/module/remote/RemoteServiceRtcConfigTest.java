package com.sentinelhub.module.remote;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.config.RemoteRtcProperties;
import com.sentinelhub.module.audit.AuditService;
import com.sentinelhub.module.device.DeviceRepository;
import com.sentinelhub.module.identity.UserRepository;
import com.sentinelhub.storage.MinioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class RemoteServiceRtcConfigTest {

    @Mock
    private RemoteRepository remoteRepository;
    @Mock
    private DeviceRepository deviceRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private MinioStorageService minioStorageService;

    private RemoteService remoteService;

    @BeforeEach
    void setUp() {
        RemoteRtcProperties props = new RemoteRtcProperties(
                List.of("stun:stun.test:3478"),
                "turn:turn.test:3478?transport=udp",
                null,
                null,
                null,
                "turn-secret",
                3600
        );
        remoteService = new RemoteService(
                remoteRepository,
                deviceRepository,
                userRepository,
                auditService,
                minioStorageService,
                props,
                new ObjectMapper()
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void getRtcConfig_exposesIceServersAndTurnFlags() {
        Map<String, Object> config = remoteService.getRtcConfig();

        assertEquals(true, config.get("turn_enabled"));
        assertEquals(true, config.get("turn_ephemeral"));

        List<Map<String, Object>> iceServers = (List<Map<String, Object>>) config.get("ice_servers");
        assertEquals(2, iceServers.size());
        assertEquals(List.of("stun:stun.test:3478"), iceServers.get(0).get("urls"));

        Map<String, Object> turn = iceServers.get(1);
        assertTrue(turn.get("username").toString().endsWith(":sentinel"));
        assertTrue(turn.containsKey("credential"));
    }
}
