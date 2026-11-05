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

import java.util.Map;

class OrderFulfillmentWorkflowTest {
    private WorkflowTestFixture<GivenWhen.Phase, Then.Phase> fixture;

    @BeforeEach
    void setUp() {
        var module = WorkflowModule.defaults("order-workflows", SimpleWorkflowContext.class)
                .definition(d -> d.autodetected(c -> new OrderFulfillmentWorkflow()));
        fixture = WorkflowTestFixture.of(module);
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
}
