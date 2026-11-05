package io.axoniq.example.order;

import org.axonframework.eventsourcing.eventstore.AppendCondition;
import org.axonframework.eventsourcing.eventstore.GenericTaggedEventMessage;
import org.axonframework.eventsourcing.eventstore.inmemory.InMemoryEventStorageEngine;
import org.axonframework.messaging.core.MessageType;
import org.axonframework.messaging.eventhandling.GenericEventMessage;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StoredEventLoggerTest {
    @Test
    void logsOnlyAfterSuccessfulCommit() {
        var original = System.out;
        var output = new ByteArrayOutputStream();
        try (var capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            var store = new StoredEventLogger(new InMemoryEventStorageEngine());
            var event = new GenericEventMessage(new MessageType("Example"), "the-payload",
                    Map.of("custom", "the-metadata"));
            var batch = List.<org.axonframework.eventsourcing.eventstore.TaggedEventMessage<?>>of(
                    new GenericTaggedEventMessage<>(event, Set.of()));

            var rolledBack = store.appendEvents(AppendCondition.none(), null, batch).join();
            rolledBack.rollback();
            assertFalse(output.toString(StandardCharsets.UTF_8).contains("the-payload"));

            var committed = store.appendEvents(AppendCondition.none(), null, batch).join();
            assertFalse(output.toString(StandardCharsets.UTF_8).contains("the-payload"));
            committed.commit().join();
            String log = output.toString(StandardCharsets.UTF_8);
            assertTrue(log.contains("the-payload"), log);
            assertTrue(log.contains("the-metadata"), log);
            assertTrue(log.contains(event.identifier()), log);
        } finally {
            System.setOut(original);
        }
    }
}
