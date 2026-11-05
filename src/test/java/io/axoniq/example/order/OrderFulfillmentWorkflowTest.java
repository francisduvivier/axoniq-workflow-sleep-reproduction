package io.axoniq.example.order;

import io.axoniq.framework.workflow.configuration.WorkflowModule;
import io.axoniq.framework.workflow.dsl.simple.SimpleWorkflowContext;
import io.axoniq.framework.workflow.runtime.test.fixture.GivenWhen;
import io.axoniq.framework.workflow.runtime.test.fixture.Then;
import io.axoniq.framework.workflow.runtime.test.fixture.WorkflowTestFixture;
import io.axoniq.framework.workflow.dsl.api.WorkflowStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderFulfillmentWorkflowTest {
    private WorkflowTestFixture<GivenWhen.Phase, Then.Phase> fixture;

    @BeforeEach
    void setUp() {
        var module = WorkflowModule.defaults("order-workflows", SimpleWorkflowContext.class)
                .definition(d -> d.autodetected(c -> new OrderFulfillmentWorkflow()));
        fixture = WorkflowTestFixture.of(module, configurer -> configurer.componentRegistry(
                registry -> StoredEventLogger.register(registry)));
    }

    @AfterEach
    void tearDown() {
        if (fixture != null) {
            fixture.then().stop();
        }
    }

    @Test
    void reservesStockAndCompletes() {
        fixture.when().publishEvent(new OrderPlaced("order-1", "customer-1", "joe@example.com", 99.95))
                .executionExists()
                .execute("reserveStock");
        fixture.then().workflowFinished(WorkflowStatus.COMPLETED)
                .stepsPassed("reserveStock")
                .payloadContains(Map.of("orderId", "order-1", "customerId", "customer-1"));
    }

    @Test
    void failsWhenStockIsUnavailable() {
        fixture.when().publishEvent(new OrderPlaced("order-2", "customer-2", "jane@example.com", 42.0))
                .executionExists()
                .executeReturning("reserveStock", Map.of("reserved", false));
        fixture.then().workflowFinished(WorkflowStatus.FAILED);
    }

    @Test
    void printsStoredOrderAndWorkflowEvents() {
        var original = System.out;
        var output = new ByteArrayOutputStream();
        try (var capture = new PrintStream(output, true, StandardCharsets.UTF_8)) {
            System.setOut(capture);
            fixture.when().publishEvent(new OrderPlaced("order-log", "customer-log", "log@example.com", 12.5))
                    .executionExists()
                    .execute("reserveStock");
            fixture.then().workflowFinished(WorkflowStatus.COMPLETED);
        } finally {
            System.setOut(original);
        }
        String log = output.toString(StandardCharsets.UTF_8);
        assertTrue(log.contains("OrderPlaced"), log);
        assertTrue(log.contains("order-log"), log);
        assertTrue(log.contains("log@example.com"), log);
        assertTrue(log.contains("OrderFulfillmentStarted"), log);
        assertTrue(log.contains("ReserveStockStarted"), log);
        assertTrue(log.contains("ReserveStockCompleted"), log);
        assertTrue(log.contains("OrderFulfillmentCompleted"), log);
        assertTrue(log.contains("workflowId"), log);
        assertTrue(log.contains("correlationId"), log);
        assertTrue(log.contains("id="), log);
        assertTrue(log.contains("metadata="), log);
        assertTrue(log.contains("timestamp="), log);
        assertTrue(log.contains("tags="), log);
    }
}
