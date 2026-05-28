package com.jacobsfam.whatsappai.service.heartbeat;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class HeartbeatConfigLoaderTest {

    @Test
    void testParseInterval() {
        HeartbeatConfigLoader loader = new HeartbeatConfigLoader();

        assertEquals(Duration.ofMinutes(30), loader.parseInterval("30m"));
        assertEquals(Duration.ofHours(2), loader.parseInterval("2h"));
        assertEquals(Duration.ofDays(1), loader.parseInterval("1d"));
        assertEquals(Duration.ofMinutes(5), loader.parseInterval("5m"));
        assertEquals(Duration.ofHours(24), loader.parseInterval("24h"));
    }

    @Test
    void testParseIntervalInvalid() {
        HeartbeatConfigLoader loader = new HeartbeatConfigLoader();

        assertThrows(IllegalArgumentException.class, () -> loader.parseInterval("30"));
        assertThrows(IllegalArgumentException.class, () -> loader.parseInterval("xyz"));
        assertThrows(IllegalArgumentException.class, () -> loader.parseInterval("30x"));
        assertThrows(IllegalArgumentException.class, () -> loader.parseInterval(""));
        assertThrows(IllegalArgumentException.class, () -> loader.parseInterval(null));
    }

    @Test
    void testParseIntervalEdgeCases() {
        HeartbeatConfigLoader loader = new HeartbeatConfigLoader();

        // Test with spaces (should fail)
        assertThrows(IllegalArgumentException.class, () -> loader.parseInterval("30 m"));

        // Test uppercase (should work - we lowercase it)
        assertEquals(Duration.ofMinutes(30), loader.parseInterval("30M"));
        assertEquals(Duration.ofHours(2), loader.parseInterval("2H"));
        assertEquals(Duration.ofDays(1), loader.parseInterval("1D"));

        // Test large values
        assertEquals(Duration.ofHours(168), loader.parseInterval("168h"));  // 1 week
        assertEquals(Duration.ofDays(7), loader.parseInterval("7d"));
    }
}
