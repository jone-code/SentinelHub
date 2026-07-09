package com.sentinelhub.module.policy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyScopeMatcherTest {

    private PolicyScopeMatcher matcher;

    @BeforeEach
    void setUp() {
        matcher = new PolicyScopeMatcher(new ObjectMapper());
    }

    @Test
    void appliesToDevice_allModeMatchesEveryone() {
        assertTrue(matcher.appliesToDevice("{\"mode\":\"all\"}", "ou-1", Set.of("g1")));
        assertTrue(matcher.appliesToDevice(null, null, Set.of()));
    }

    @Test
    void appliesToDevice_orgUnitModeMatchesAssignedUnit() {
        String scope = "{\"mode\":\"org_unit\",\"ids\":[\"ou-1\",\"ou-2\"]}";
        assertTrue(matcher.appliesToDevice(scope, "ou-1", Set.of()));
        assertFalse(matcher.appliesToDevice(scope, "ou-9", Set.of()));
    }

    @Test
    void appliesToDevice_deviceGroupModeMatchesAnyGroup() {
        String scope = "{\"mode\":\"device_group\",\"ids\":[\"g1\",\"g2\"]}";
        assertTrue(matcher.appliesToDevice(scope, null, Set.of("g2", "g9")));
        assertFalse(matcher.appliesToDevice(scope, null, Set.of("g3")));
    }

    @Test
    void appliesToDevice_scopedModeWithEmptyIdsDoesNotMatch() {
        assertFalse(matcher.appliesToDevice("{\"mode\":\"org_unit\",\"ids\":[]}", "ou-1", Set.of()));
    }
}
