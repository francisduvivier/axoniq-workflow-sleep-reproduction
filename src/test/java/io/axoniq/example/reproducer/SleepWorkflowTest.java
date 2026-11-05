package io.axoniq.example.reproducer;

import io.axoniq.framework.axonserver.connector.configuration.AxonServerConfigurationEnhancer;
import io.axoniq.framework.workflow.configuration.WorkflowModule;
import io.axoniq.framework.workflow.dsl.api.WorkflowStatus;
import io.axoniq.framework.workflow.dsl.simple.SimpleWorkflowContext;
import io.axoniq.framework.workflow.runtime.test.fixture.WorkflowTestFixture;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

class SleepWorkflowTest {
    @Test
    void waitsOnlyForTheTimeRemainingUntilTheDeadline() {
        var clock = Clock.fixed(Instant.parse("2026-01-01T12:00:03Z"), ZoneOffset.UTC);
        var module = WorkflowModule.defaults("sleep-test", SimpleWorkflowContext.class)
                .definition(d -> d.autodetected(c -> new SleepWorkflow(clock)));
        var fixture = WorkflowTestFixture.of(module, configurer -> configurer.componentRegistry(
                registry -> registry.disableEnhancer(AxonServerConfigurationEnhancer.class)));
        try {
            fixture.when().publishEvent(new SleepScheduled("timer-1", "2026-01-01T12:00:10Z"))
                    .executionExists();
            fixture.then().waitingIn("wait");
            fixture.when().timePasses(Duration.ofSeconds(6));
            fixture.then().waitingIn("wait");
            fixture.when().timePasses(Duration.ofSeconds(1));
            fixture.then().workflowFinished(WorkflowStatus.COMPLETED);
        } finally {
            fixture.then().stop();
        }
    }
}
