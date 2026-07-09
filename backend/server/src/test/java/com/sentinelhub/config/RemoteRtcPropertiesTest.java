package com.sentinelhub.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteRtcPropertiesTest {

    @Test
    void resolvedStunServers_usesDefaultWhenEmpty() {
        RemoteRtcProperties props = new RemoteRtcProperties(null, null, null, null, null, null, null);
        assertEquals(List.of("stun:stun.l.google.com:19302"), props.resolvedStunServers());
    }

    @Test
    void resolvedTurnUrls_splitsCommaSeparatedTurnUrl() {
        RemoteRtcProperties props = new RemoteRtcProperties(
                null,
                "turn:a:3478?transport=udp, turn:b:3478?transport=tcp",
                null,
                null,
                null,
                null,
                null
        );
        assertEquals(
                List.of("turn:a:3478?transport=udp", "turn:b:3478?transport=tcp"),
                props.resolvedTurnUrls()
        );
    }

    @Test
    void buildIceServers_includesEphemeralTurnCredentialsWhenSecretSet() {
        RemoteRtcProperties props = new RemoteRtcProperties(
                List.of("stun:stun.example.com:3478"),
                "turn:turn.example.com:3478?transport=udp",
                null,
                null,
                null,
                "shared-secret",
                7200
        );

        List<Map<String, Object>> servers = props.buildIceServers();
        assertEquals(2, servers.size());
        assertEquals(List.of("stun:stun.example.com:3478"), servers.get(0).get("urls"));

        Map<String, Object> turn = servers.get(1);
        assertEquals("turn:turn.example.com:3478?transport=udp", turn.get("urls"));
        assertTrue(turn.get("username").toString().endsWith(":sentinel"));
        assertTrue(turn.containsKey("credential"));
        assertTrue(props.usesEphemeralCredentials());
        assertTrue(props.hasTurn());
    }

    @Test
    void buildIceServers_usesStaticCredentialsWithoutSecret() {
        RemoteRtcProperties props = new RemoteRtcProperties(
                null,
                "turn:localhost:3478",
                null,
                "static-user",
                "static-pass",
                null,
                null
        );

        Map<String, Object> turn = props.buildIceServers().get(1);
        assertEquals("static-user", turn.get("username"));
        assertEquals("static-pass", turn.get("credential"));
        assertFalse(props.usesEphemeralCredentials());
    }

    @Test
    void buildIceServers_stunOnlyWhenNoTurnUrl() {
        RemoteRtcProperties props = new RemoteRtcProperties(null, null, null, null, null, null, null);
        assertEquals(1, props.buildIceServers().size());
        assertFalse(props.hasTurn());
    }
}
