package io.axoniq.example.reproducer;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReproducerAppTest {
    @Test
    void reportsAnEarlyTimeoutAgainstThePersistedDeadline() {
        var output = new ByteArrayOutputStream();
        var original = System.out;
        try (var capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            assertTrue(ReproducerApp.report(
                    Instant.parse("2026-01-01T12:01:00Z"), Instant.parse("2026-01-01T12:00:54.390Z")));
        } finally {
            System.setOut(original);
        }
        String log = output.toString(StandardCharsets.UTF_8);
        assertTrue(log.contains("BUG REPRODUCED: timeout occurred 5.610 seconds early"));
        assertTrue(log.contains("expected=12:01:00"));
        assertTrue(log.contains("actual=12:00:54"));
        assertFalse(log.contains("2026-"));
        assertFalse(log.contains("Z"));
    }

    @Test
    void doesNotReportOnTimeOrLateTimeoutsAsReproductions() {
        var deadline = Instant.parse("2026-01-01T12:01:00Z");
        assertFalse(ReproducerApp.report(deadline, deadline));
        assertFalse(ReproducerApp.report(deadline, deadline.plusSeconds(2)));
        assertFalse(ReproducerApp.report(deadline, deadline.minusSeconds(1)));
    }
}
